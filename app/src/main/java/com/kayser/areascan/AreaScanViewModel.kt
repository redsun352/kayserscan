package com.kayser.areascan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kayser.areascan.core.interpolation.RbfInterpolator
import com.kayser.areascan.core.model.AreaScanSettings
import com.kayser.areascan.core.model.ScanPoint
import com.kayser.areascan.render.opengl.GridMeshBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AreaScanActivity'nin durumunu yönetir: ayar paneli değişikliklerini dinler,
 * tarama noktalarından mesh üretimini tetikler.
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
    }

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
