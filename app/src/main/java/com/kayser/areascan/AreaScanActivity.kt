package com.kayser.areascan

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigationrail.NavigationRailView
import com.kayser.areascan.fragments.FragmentBoundingBoxProperties
import com.kayser.areascan.fragments.FragmentGridProperties
import com.kayser.areascan.fragments.FragmentMarchingCubesProperties
import com.kayser.areascan.fragments.FragmentRbfProperties
import com.kayser.areascan.fragments.FragmentScanningModeSettings
import com.kayser.areascan.fragments.FragmentSoilProperties
import com.kayser.areascan.fragments.FragmentSurfaceProperties
import com.kayser.areascan.render.widgets.ModelSurfaceView
import com.kayser.areascan.sensor.MagnetometerService
import com.kayser.areascan.sensor.RawMagneticSample
import com.kayser.areascan.sensor.ScanSessionRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Yüzey Tarama ana ekranı.
 *
 * Mimari Thuban Lodestar'daki AreaScanActivity'nin decompile analizinden çıkarılan
 * akışı izler: ModelSurfaceView merkezde, NavigationRailView ile yan panelden 7 ayar
 * grubuna geçilir, manyetometre verisi ayrı bir Service'ten (burada [MagnetometerService])
 * gelir. Servis bağlantısı, statik field+callback yerine modern bound-service + Flow ile yapılır.
 */
class AreaScanActivity : AppCompatActivity() {

    private val viewModel: AreaScanViewModel by lazy {
        ViewModelProvider(this)[AreaScanViewModel::class.java]
    }

    private val scanSessionRepository = ScanSessionRepository()

    private var magnetometerService: MagnetometerService? = null
    private var serviceBound = false

    private lateinit var glView: ModelSurfaceView
    private lateinit var lastValueTextView: TextView

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? MagnetometerService.LocalBinder
            magnetometerService = localBinder?.getService()
            serviceBound = true
            observeSensorSamples()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            magnetometerService = null
            serviceBound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startMagnetometerService()
        // İzin verilmezse: servis foreground bildirimsiz başlatılamaz (Android 13+).
        // UI tarafında bir uyarı gösterilebilir; şimdilik sessizce devam.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_area_scan)

        glView = findViewById(R.id.mGLView)
        lastValueTextView = findViewById(R.id.lastValueTextview)

        setupNavigationRail()
        setupCameraButtons()
        setupScanningInteraction()
        observeViewModel()

        requestNotificationPermissionIfNeeded()
    }

    /**
     * Tarama modunu etkinleştirir (kamera top-down kilitlenir) ve basılı tutma
     * jestini gerçek ölçüm kaydına bağlar.
     */
    private fun setupScanningInteraction() {
        glView.setScanningMode(true)

        glView.onCellLongPress = { worldX, worldZ ->
            commitAveragedMeasurement(worldX, worldZ)
            glView.modelRenderer.highlightedCell = null
        }

        glView.onLongPressProgress = { progress ->
            // Basit geri bildirim: ilerleme yüzdesini anlık değer metninin üstüne yazıyoruz.
            // Daha sonra dairesel bir progress widget'ı ile değiştirilebilir.
            lastValueTextView.text = "Ölçülüyor… %d%%".format((progress * 100).toInt())
        }

        glView.onLongPressCancelled = {
            lastValueTextView.text = "İptal edildi"
        }
    }

    private fun setupNavigationRail() {
        val navRail = findViewById<NavigationRailView>(R.id.navigationRailView)
        navRail.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.area_scan_navigationview_scanning -> FragmentScanningModeSettings()
                R.id.area_scan_navigationview_grid -> FragmentGridProperties()
                R.id.area_scan_navigationview_boundingbox -> FragmentBoundingBoxProperties()
                R.id.area_scan_navigationview_surface -> FragmentSurfaceProperties()
                R.id.area_scan_navigationview_rbf -> FragmentRbfProperties()
                R.id.area_scan_navigationview_soil -> FragmentSoilProperties()
                R.id.area_scan_navigationview_marching_cubes -> FragmentMarchingCubesProperties()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, fragment)
                .commit()
            true
        }
    }

    private fun setupCameraButtons() {
        findViewById<FloatingActionButton>(R.id.cameraRotationResetButton).setOnClickListener {
            glView.resetCameraRotation()
        }
        findViewById<FloatingActionButton>(R.id.cameraZoomResetButton).setOnClickListener {
            glView.resetCameraZoom()
        }
    }

    private fun observeViewModel() {
        launchOnStarted {
            viewModel.meshData.onEach { mesh ->
                val wireframe = viewModel.settings.value.surface.wireframe
                glView.modelRenderer.updateMesh(mesh, wireframe)
            }.launchIn(this)

            viewModel.lastValueText.onEach { text ->
                lastValueTextView.text = text
            }.launchIn(this)
        }
    }

    /** repeatOnLifecycle çağrısını tek satırda toparlayan küçük yardımcı. */
    private fun launchOnStarted(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                block()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                startMagnetometerService()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startMagnetometerService()
        }
    }

    private fun startMagnetometerService() {
        val intent = Intent(this, MagnetometerService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** Basılı tutma sırasında biriken son ham örnekler; long-press tetiklenince ortalanır. */
    private val recentSamplesBuffer = ConcurrentLinkedDeque<RawMagneticSample>()

    private fun observeSensorSamples() {
        val service = magnetometerService ?: return
        launchOnStarted {
            service.samples.onEach { sample ->
                // Sürekli grid'e eklemek yerine, sadece son örnekleri kısa bir tamponda tutuyoruz.
                // Gerçek ölçüm noktası, kullanıcı 3D görünümde bir konuma basılı tuttuğunda
                // (onCellLongPress) bu tampondaki örneklerin ortalaması alınarak kaydedilir.
                recentSamplesBuffer.addLast(sample)
                while (recentSamplesBuffer.size > SAMPLE_BUFFER_SIZE) {
                    recentSamplesBuffer.pollFirst()
                }
                lastValueTextView.text = "%.2f µT (anlık)".format(sample.magnitude)
            }.launchIn(this)
        }
    }

    /** Basılı tutma bittiğinde tampondaki örneklerin ortalamasını alıp ölçüm noktası olarak kaydeder. */
    private fun commitAveragedMeasurement(worldX: Float, worldZ: Float) {
        val samples = recentSamplesBuffer.toList()
        if (samples.isEmpty()) {
            return
        }

        val avgX = samples.map { it.x }.average().toFloat()
        val avgY = samples.map { it.y }.average().toFloat()
        val avgZ = samples.map { it.z }.average().toFloat()
        val avgBiasX = samples.map { it.biasX }.average().toFloat()
        val avgBiasY = samples.map { it.biasY }.average().toFloat()
        val avgBiasZ = samples.map { it.biasZ }.average().toFloat()
        val lastTimestamp = samples.last().timestampNanos
        val lastAccuracy = samples.last().accuracy

        val averagedSample = RawMagneticSample(
            x = avgX, y = avgY, z = avgZ,
            biasX = avgBiasX, biasY = avgBiasY, biasZ = avgBiasZ,
            timestampNanos = lastTimestamp, accuracy = lastAccuracy
        )

        // Grid düzlemindeki dünya koordinatı (metre) doğrudan ScanPoint x/y'sine yazılır.
        // worldZ, dünya Z eksenidir; ScanPoint'in "y" alanına karşılık gelir (bkz. GridMeshBuilder).
        scanSessionRepository.addSample(averagedSample, x = worldX, y = worldZ)
        val points = scanSessionRepository.points.value
        if (points.isNotEmpty()) {
            viewModel.onScanPointAdded(points.last())
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }

    companion object {
        /** Basılı tutma sırasında ortalama alınacak en yakın örnek sayısı (~50Hz'de yaklaşık 1 saniyelik pencere). */
        private const val SAMPLE_BUFFER_SIZE = 50
    }
}
