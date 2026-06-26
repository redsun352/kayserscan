package com.kayser.areascan.sensor

import kotlin.math.max
import kotlin.math.min

/**
 * Hard-iron kalibrasyonu: cihazı çeşitli yönlerde döndürerek toplanan ham X/Y/Z örneklerinden
 * min/max sınırlarını çıkarıp ofset (bias) merkezini hesaplar.
 *
 * Bu, Hasan'ın ArkeoMag projesindeki Canvas 2D scatter-plot kalibrasyon ekranıyla aynı
 * matematiksel temele dayanır: küre/elipsoid merkezini min-max ortalamasından kestirir.
 *
 * Not: Bu basit min-max merkezleme yöntemidir. Soft-iron (eliptik distorsiyon) düzeltmesi
 * için ayrıca bir elipsoid-fit (en küçük kareler) algoritması eklenebilir — şimdilik kapsam dışı.
 */
class HardIronCalibrator {

    private var minX = Float.MAX_VALUE
    private var maxX = -Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var maxY = -Float.MAX_VALUE
    private var minZ = Float.MAX_VALUE
    private var maxZ = -Float.MAX_VALUE

    private var sampleCount = 0

    /** Kalibrasyon sırasında her örnek için çağrılır. */
    fun addSample(x: Float, y: Float, z: Float) {
        minX = min(minX, x); maxX = max(maxX, x)
        minY = min(minY, y); maxY = max(maxY, y)
        minZ = min(minZ, z); maxZ = max(maxZ, z)
        sampleCount++
    }

    fun reset() {
        minX = Float.MAX_VALUE; maxX = -Float.MAX_VALUE
        minY = Float.MAX_VALUE; maxY = -Float.MAX_VALUE
        minZ = Float.MAX_VALUE; maxZ = -Float.MAX_VALUE
        sampleCount = 0
    }

    val collectedSampleCount: Int get() = sampleCount

    /** Min-max aralığının yeterince geniş olup olmadığını kontrol eder (kalibrasyon kalitesi göstergesi). */
    fun isSufficient(minRangeMicroTesla: Float = 10f): Boolean {
        if (sampleCount < 20) return false
        val rangeX = maxX - minX
        val rangeY = maxY - minY
        val rangeZ = maxZ - minZ
        return rangeX > minRangeMicroTesla && rangeY > minRangeMicroTesla && rangeZ > minRangeMicroTesla
    }

    /** Hesaplanan hard-iron ofset merkezi. */
    fun computeOffset(): HardIronOffset = HardIronOffset(
        offsetX = (minX + maxX) / 2f,
        offsetY = (minY + maxY) / 2f,
        offsetZ = (minZ + maxZ) / 2f
    )
}

data class HardIronOffset(
    val offsetX: Float,
    val offsetY: Float,
    val offsetZ: Float
) {
    fun apply(x: Float, y: Float, z: Float): Triple<Float, Float, Float> =
        Triple(x - offsetX, y - offsetY, z - offsetZ)

    companion object {
        val IDENTITY = HardIronOffset(0f, 0f, 0f)
    }
}
