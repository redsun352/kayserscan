package com.kayser.areascan.sensor

/**
 * TYPE_MAGNETIC_FIELD_UNCALIBRATED sensöründen gelen ham örnek.
 *
 * Android dokümantasyonuna göre values[] dizisi 6 elemanlıdır:
 * [0..2] = ham manyetik alan (µT, bias düzeltmesi yapılmamış)
 * [3..5] = tahmini bias (µT) — fabrika/sensör füzyon kestirimi, hard-iron kalibrasyonun yerini TAM tutmaz
 */
data class RawMagneticSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val biasX: Float,
    val biasY: Float,
    val biasZ: Float,
    val timestampNanos: Long,
    val accuracy: Int
) {
    /** Bias düzeltmesi uygulanmış ama henüz hard-iron kalibrasyonu uygulanmamış değer. */
    val correctedX: Float get() = x - biasX
    val correctedY: Float get() = y - biasY
    val correctedZ: Float get() = z - biasZ

    val magnitude: Float
        get() = kotlin.math.sqrt(correctedX * correctedX + correctedY * correctedY + correctedZ * correctedZ)
}
