package com.kayser.areascan.sensor

import com.kayser.areascan.core.model.ScanPoint
import com.kayser.areascan.core.model.SoilConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tek bir tarama oturumu boyunca toplanan [ScanPoint] listesini yönetir.
 *
 * Konum bilgisi şu an için basit bir "kullanıcı/UI tarafından sağlanan X/Y" modeliyle
 * çalışır (örn. grid üzerinde dokunma noktası veya adım sayacı/IMU ile dead-reckoning).
 * GPS/step-counter entegrasyonu ileride bu sınıfa eklenebilir; ScanPoint.x/y alanları
 * zaten bu genişlemeye hazır şekilde tasarlandı.
 */
class ScanSessionRepository(
    private val hardIronCalibrator: HardIronCalibrator = HardIronCalibrator()
) {
    private val _points = MutableStateFlow<List<ScanPoint>>(emptyList())
    val points = _points.asStateFlow()

    private var calibrationOffset = HardIronOffset.IDENTITY
    private var soilConfig: SoilConfig = SoilConfig()

    private val baselineSamples = mutableListOf<Float>()
    private var computedBaseline: Float? = null

    fun setCalibrationOffset(offset: HardIronOffset) {
        calibrationOffset = offset
    }

    fun setSoilConfig(config: SoilConfig) {
        soilConfig = config
        if (!config.autoBaseline) {
            computedBaseline = config.baselineMagnitude
        }
    }

    /**
     * Yeni bir ham sensör örneğini, verilen tarama koordinatıyla birleştirip
     * kalibrasyon + toprak taban normalizasyonu uygulayarak listeye ekler.
     */
    fun addSample(raw: RawMagneticSample, x: Float, y: Float, z: Float = 0f) {
        val (cx, cy, cz) = calibrationOffset.apply(raw.correctedX, raw.correctedY, raw.correctedZ)
        val magnitude = kotlin.math.sqrt(cx * cx + cy * cy + cz * cz)

        if (soilConfig.autoBaseline && computedBaseline == null) {
            baselineSamples.add(magnitude)
            if (baselineSamples.size >= soilConfig.autoBaselineSampleCount) {
                computedBaseline = baselineSamples.average().toFloat()
            }
        }

        val point = ScanPoint(
            x = x,
            y = y,
            z = z,
            magnitude = magnitude,
            rawX = raw.x,
            rawY = raw.y,
            rawZ = raw.z,
            timestampNanos = raw.timestampNanos,
            accuracy = raw.accuracy
        )
        point.normalizedValue = computedBaseline?.let { magnitude - it }

        _points.value = _points.value + point
    }

    fun clear() {
        _points.value = emptyList()
        baselineSamples.clear()
        computedBaseline = null
    }

    fun exportSnapshot(): List<ScanPoint> = _points.value
}
