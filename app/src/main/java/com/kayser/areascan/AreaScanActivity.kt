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
import com.kayser.areascan.sensor.ScanSessionRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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
        observeViewModel()

        requestNotificationPermissionIfNeeded()
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

    private fun observeSensorSamples() {
        val service = magnetometerService ?: return
        launchOnStarted {
            service.samples.onEach { sample ->
                // NOT: Gerçek tarama konumu (x,y) burada henüz GPS/step-counter'dan gelmiyor.
                // Şimdilik basit bir zaman-bazlı placeholder konum kullanılıyor; ileride
                // kullanıcı dokunma noktası ya da konum servisinden gelen X/Y ile değiştirilecek.
                val elapsedSeconds = (sample.timestampNanos / 1_000_000_000L % 1000).toFloat()
                val x = (elapsedSeconds % viewModel.settings.value.grid.widthMeters)
                val y = ((elapsedSeconds / 10f) % viewModel.settings.value.grid.heightMeters)

                scanSessionRepository.addSample(sample, x = x, y = y)
                val points = scanSessionRepository.points.value
                if (points.isNotEmpty()) {
                    viewModel.onScanPointAdded(points.last())
                }
            }.launchIn(this)
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }
}
