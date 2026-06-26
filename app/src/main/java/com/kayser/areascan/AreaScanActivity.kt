package com.kayser.areascan

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
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

    /** Tarama modunda son seçilen (ama henüz ölçülmemiş) hücrenin dünya koordinatı. */
    private var selectedWorldX: Float? = null
    private var selectedWorldZ: Float? = null

    /** Buton basılı tutulurken çalışan ilerleme/tamamlama Handler'ı. */
    private val measurementHandler = Handler(Looper.getMainLooper())
    private var measurementTriggered = false

    private lateinit var takeMeasurementButton: MaterialButton
    private lateinit var measurementProgressBar: ProgressBar

    /** Adımlı tarama modunda durak noktasına ulaşıldığında dikkat çekmek için kullanılan beep. */
    private val toneGenerator by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME) }

    private val measurementProgressRunnable = object : Runnable {
        private var startTime = 0L
        fun start() {
            startTime = System.currentTimeMillis()
            measurementHandler.postDelayed(this, PROGRESS_TICK_MS)
        }
        override fun run() {
            if (measurementTriggered) return
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / MEASUREMENT_HOLD_DURATION_MS).coerceIn(0f, 1f)
            measurementProgressBar.progress = (progress * 100).toInt()
            if (progress >= 1f) {
                triggerMeasurement()
            } else {
                measurementHandler.postDelayed(this, PROGRESS_TICK_MS)
            }
        }
    }

    /**
     * Tarama modunu etkinleştirir (kamera top-down kilitlenir). İki alt-mod desteklenir:
     *
     * A) Serbest seçim modu (varsayılan): 3D görünümde kısa dokunuşla (tap) istediğin
     *    herhangi bir hücre seçilir.
     *
     * B) Adımlı tarama modu (Tarama Ayarları panelinden başlatılır): sistem sırayla
     *    önceden hesaplanmış waypoint'leri otomatik seçili gösterir; her yeni durakta
     *    beep çalınır. Kullanıcı sadece cihazı o noktaya götürüp "Ölçü Al"a basar;
     *    ölçüm tamamlanınca sistem otomatik olarak sıradaki durağa geçer.
     *
     * Her iki modda da gerçek ölçüm: "Ölçü Al" butonu basılı tutulduğu sürece
     * (MEASUREMENT_HOLD_DURATION_MS) ilerleme çubuğu dolar; süre tamamlanınca o anki
     * sensör tamponunun ortalaması seçili koordinata kaydedilir.
     */
    private fun setupScanningInteraction() {
        glView.setScanningMode(true)
        takeMeasurementButton = findViewById(R.id.takeMeasurementButton)
        measurementProgressBar = findViewById(R.id.measurementProgressBar)

        glView.onCellSelected = { worldX, worldZ ->
            // Adımlı tarama çalışırken kullanıcının serbest seçim yapmasını engelliyoruz;
            // konum zaten sıradaki waypoint tarafından dayatılıyor.
            if (!viewModel.isSteppedScanRunning()) {
                selectedWorldX = worldX
                selectedWorldZ = worldZ
                takeMeasurementButton.isEnabled = true
            }
        }

        takeMeasurementButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (selectedWorldX != null) {
                        startMeasurementHold()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelMeasurementHold()
                    if (event.action == MotionEvent.ACTION_UP) {
                        takeMeasurementButton.performClick()
                    }
                    true
                }
                else -> false
            }
        }
        // performClick gerekli (erişilebilirlik/lint uyarısını gidermek için) ama gerçek
        // ölçüm mantığı zaten OnTouchListener'da yönetiliyor; burada ek bir işlem yapılmıyor.
        takeMeasurementButton.setOnClickListener { /* no-op: bkz. OnTouchListener */ }

        observeSteppedScanWaypoints()
    }

    /**
     * Adımlı tarama waypoint state'ini izler: her yeni durak geldiğinde (ya tarama
     * başlatıldığında ya da bir ölçüm tamamlanıp sıradaki durağa geçildiğinde)
     * o koordinatı otomatik seçili yapar, 3D görünümde vurgular ve beep çalar.
     */
    private fun observeSteppedScanWaypoints() {
        launchOnStarted {
            viewModel.currentWaypointFlow.onEach { waypoint ->
                if (!viewModel.isSteppedScanRunning() || waypoint == null) return@onEach

                selectedWorldX = waypoint.worldX
                selectedWorldZ = waypoint.worldZ
                glView.modelRenderer.highlightedCell = floatArrayOf(waypoint.worldX, waypoint.worldZ)
                takeMeasurementButton.isEnabled = true
                playWaypointBeep()

                val total = viewModel.waypoints.value.size
                lastValueTextView.text = getString(R.string.waypoint_progress_format, waypoint.index + 1, total)
            }.launchIn(this)
        }
    }

    private fun playWaypointBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
    }

    private fun startMeasurementHold() {
        measurementTriggered = false
        measurementProgressBar.visibility = View.VISIBLE
        measurementProgressBar.progress = 0
        measurementProgressRunnable.start()
    }

    private fun cancelMeasurementHold() {
        if (!measurementTriggered) {
            measurementHandler.removeCallbacks(measurementProgressRunnable)
            measurementProgressBar.visibility = View.INVISIBLE
            measurementProgressBar.progress = 0
        }
    }

    private fun triggerMeasurement() {
        measurementTriggered = true
        val worldX = selectedWorldX
        val worldZ = selectedWorldZ
        measurementProgressBar.visibility = View.INVISIBLE
        measurementProgressBar.progress = 0

        if (worldX != null && worldZ != null) {
            commitAveragedMeasurement(worldX, worldZ)
        }

        selectedWorldX = null
        selectedWorldZ = null
        glView.clearSelection()
        takeMeasurementButton.isEnabled = false

        // Adımlı tarama çalışıyorsa otomatik olarak sıradaki durağa geç.
        // advanceToNextWaypoint() false dönerse (son durak tamamlandı) tarama biter.
        if (viewModel.isSteppedScanRunning()) {
            val hasNext = viewModel.advanceToNextWaypoint()
            if (!hasNext) {
                lastValueTextView.text = getString(R.string.stepped_scan_complete_text)
            }
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
        toneGenerator.release()
        super.onDestroy()
    }

    companion object {
        /** Basılı tutma sırasında ortalama alınacak en yakın örnek sayısı (~50Hz'de yaklaşık 1 saniyelik pencere). */
        private const val SAMPLE_BUFFER_SIZE = 50

        /** "Ölçü Al" butonunun ne kadar süre basılı tutulması gerektiği (ms). */
        private const val MEASUREMENT_HOLD_DURATION_MS = 1200L

        /** İlerleme çubuğunun ne sıklıkla güncellendiği (ms). */
        private const val PROGRESS_TICK_MS = 50L

        /** Adımlı tarama durağına ulaşıldığında çalınan beep'in süresi (ms). */
        private const val BEEP_DURATION_MS = 150

        /** ToneGenerator ses seviyesi (0-100). */
        private const val TONE_VOLUME = 80
    }
}
