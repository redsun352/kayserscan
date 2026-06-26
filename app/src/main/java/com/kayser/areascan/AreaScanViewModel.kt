package com.kayser.areascan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kayser.areascan.core.interpolation.RbfInterpolator
import com.kayser.areascan.core.model.AreaScanSettings
import com.kayser.areascan.core.model.ScanPoint
import com.kayser.areascan.core.model.SteppedScanPathPlanner
import com.kayser.areascan.core.model.Waypoint
import com.kayser.areascan.render.opengl.GridMeshBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AreaScanActivity'nin durumunu yönetir: ayar paneli değişikliklerini dinler,
 * tarama noktalarından mesh üretimini tetikler, adımlı tarama akışındaki
 * waypoint sırasını yönetir.
 *
 * Thuban'daki AreaScanActivity'nin field'ları (grid/surface/rbf/soil/marchingCubes config)
 * burada tek bir [AreaScanSettings] state objesi olarak modellendi.
 */
class AreaScanViewModel : ViewModel() {

    private val _settings = MutableStateFlow(AreaScanSettings())
    val settings: StateFlow<AreaScanSettings> = _settings.asStateFlow()

    private val _scanPoints = MutableStateFlow<List<ScanPoint>>(emptyList())
    val scanPoints: StateFlow<List<ScanPoint>> = _scanPoints.asStateFlow()

    private val _meshData = MutableStateFlow<GridMeshBuilder.MeshData?>(null)
    val meshData: StateFlow<GridMeshBuilder.MeshData?> = _meshData.asStateFlow()

    private val _lastValueText = MutableStateFlow("—")
    val lastValueText: StateFlow<String> = _lastValueText.asStateFlow()

    // --- Adımlı tarama (stepped scan) state'i ---
    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _currentWaypointIndex = MutableStateFlow(0)
    val currentWaypointIndex: StateFlow<Int> = _currentWaypointIndex.asStateFlow()

    /** Adımlı tarama aktif mi (waypoint listesi oluşturulup başlatılmış mı). */
    private val _isSteppedScanActive = MutableStateFlow(false)
    val isSteppedScanActive: StateFlow<Boolean> = _isSteppedScanActive.asStateFlow()

    /**
     * Mevcut waypoint'i (varsa) yayan birleşik akış.
     *
     * Neden ayrı bir StateFlow yerine combine? `currentWaypointIndex` her zaman 0'dan
     * başladığı için (yeni tarama başlatılınca da 0'a sıfırlanıyor), sadece index'i
     * gözlemlemek StateFlow'un "değer değişmedi" davranışı yüzünden ikinci taramanın
     * ilk durağını atlayabilirdi. waypoints listesinin kendisi de değiştiği için bu
     * combine her yeni tarama başlangıcında garanti olarak yeniden tetiklenir.
     */
    val currentWaypointFlow = combine(_waypoints, _currentWaypointIndex) { list, index ->
        list.getOrNull(index)
    }

    val currentWaypoint: Waypoint?
        get() = _waypoints.value.getOrNull(_currentWaypointIndex.value)

    private val gridMeshBuilder = GridMeshBuilder()
    private val rbfInterpolator = RbfInterpolator()

    fun updateSettings(transform: (AreaScanSettings) -> AreaScanSettings) {
        _settings.value = transform(_settings.value)
        rebuildMesh()
    }

    fun onScanPointAdded(point: ScanPoint) {
        _scanPoints.value = _scanPoints.value + point
        _lastValueText.value = "%.2f µT".format(point.normalizedValue ?: point.magnitude)
        rebuildMesh()
    }

    fun clearScan() {
        _scanPoints.value = emptyList()
        _meshData.value = null
        _lastValueText.value = "—"
        stopSteppedScan()
    }

    /**
     * Adımlı tarama akışını başlatır: mevcut grid ve adım aralığı ayarlarından
     * sıralı waypoint listesi üretir, ilk durağa konumlanır.
     * @return üretilen waypoint sayısı (0 ise adım aralığı/ızgara ayarı geçersiz).
     */
    fun startSteppedScan(): Int {
        val s = _settings.value
        val generated = SteppedScanPathPlanner.buildWaypoints(
            grid = s.grid,
            stepIntervalMeters = s.steppedScan.stepIntervalMeters
        )
        _waypoints.value = generated
        _currentWaypointIndex.value = 0
        _isSteppedScanActive.value = generated.isNotEmpty()
        return generated.size
    }

    fun stopSteppedScan() {
        _isSteppedScanActive.value = false
        _waypoints.value = emptyList()
        _currentWaypointIndex.value = 0
    }

    /** Mevcut waypoint'te ölçüm tamamlandığında çağrılır; sıradaki durağa geçer. */
    fun advanceToNextWaypoint(): Boolean {
        val next = _currentWaypointIndex.value + 1
        return if (next < _waypoints.value.size) {
            _currentWaypointIndex.value = next
            true
        } else {
            _isSteppedScanActive.value = false
            false
        }
    }

    fun isSteppedScanRunning(): Boolean = _isSteppedScanActive.value

    /** RBF enterpolasyonu + mesh üretimini arka planda (Default dispatcher) çalıştırır. */
    private fun rebuildMesh() {
        val points = _scanPoints.value
        if (points.isEmpty()) return

        val currentSettings = _settings.value
        viewModelScope.launch {
            val mesh = withContext(Dispatchers.Default) {
                val interpolatedGrid = rbfInterpolator.interpolate(
                    points = points,
                    config = currentSettings.rbf,
                    gridResolution = currentSettings.grid.resolution * currentSettings.rbf.outputResolutionMultiplier,
                    domainWidth = currentSettings.grid.widthMeters,
                    domainHeight = currentSettings.grid.heightMeters
                )
                gridMeshBuilder.buildFromInterpolatedGrid(
                    valueGrid = interpolatedGrid,
                    grid = currentSettings.grid,
                    surface = currentSettings.surface
                )
            }
            _meshData.value = mesh
        }
    }
}
