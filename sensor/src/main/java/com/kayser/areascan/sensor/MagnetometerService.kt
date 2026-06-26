package com.kayser.areascan.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Telefonun dahili manyetometresini okuyan foreground servis.
 *
 * Mimari, Thuban Lodestar APK'sının `MagnetometerService` sınıfının decompile/analiz
 * edilmesinden çıkarılan bulgulara dayanır:
 *  - Sensör tipi: TYPE_MAGNETIC_FIELD_UNCALIBRATED (sensor type kodu 14)
 *  - Örnekleme hızı: SensorManager.SENSOR_DELAY_GAME (~50 Hz)
 *  - Activity'den ayrı bir Service olarak çalışır (ekran kapansa/Activity yok olsa da veri akışı sürebilir)
 *
 * Buradaki versiyon, statik field + Function1 callback yerine Kotlin Flow kullanır ve
 * foreground notification + bound-service erişimi ekler.
 */
class MagnetometerService : Service(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var magnetometer: Sensor? = null

    private val _state = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val state = _state.asStateFlow()

    private val _samples = MutableSharedFlow<RawMagneticSample>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val samples = _samples.asSharedFlow()

    inner class LocalBinder : Binder() {
        fun getService(): MagnetometerService = this@MagnetometerService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorManager = (getSystemService(SENSOR_SERVICE) as? SensorManager)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sensor = magnetometer
        if (sensor == null) {
            _state.value = ServiceState.Error(ErrorReason.SENSOR_UNAVAILABLE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        val registered = sensorManager?.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME
        ) ?: false

        _state.value = if (registered) ServiceState.Running else ServiceState.Error(ErrorReason.REGISTER_FAILED)

        // START_STICKY: sistem servisi öldürürse (bellek baskısı) yeniden başlatmaya çalışır.
        // Saha taramasında uzun süreli arka plan çalışması istendiği için tercih edildi.
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (_state.value !is ServiceState.Running) return

        // values[0..2] = ham alan, values[3..5] = sensörün kendi bias kestirimi
        val sample = RawMagneticSample(
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
            biasX = event.values.getOrElse(3) { 0f },
            biasY = event.values.getOrElse(4) { 0f },
            biasZ = event.values.getOrElse(5) { 0f },
            timestampNanos = event.timestamp,
            accuracy = event.accuracy
        )

        // tryEmit: sensör thread'i bloklamamak için non-suspending emit
        _samples.tryEmit(sample)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // TYPE_MAGNETIC_FIELD_UNCALIBRATED için accuracy değeri genelde anlamlı değildir,
        // ama gelecekte kalibre edilmiş sensöre geçiş ihtimaline karşı saklanır.
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this, magnetometer)
        _state.value = ServiceState.Idle
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Yüzey Tarama Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Manyetometre verisi arka planda okunurken gösterilir."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Yüzey Tarama Çalışıyor")
            .setContentText("Manyetometre verisi okunuyor")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    sealed class ServiceState {
        object Idle : ServiceState()
        object Running : ServiceState()
        data class Error(val reason: ErrorReason) : ServiceState()
    }

    enum class ErrorReason {
        SENSOR_UNAVAILABLE,
        REGISTER_FAILED
    }

    companion object {
        private const val CHANNEL_ID = "magnetometer_scan_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
