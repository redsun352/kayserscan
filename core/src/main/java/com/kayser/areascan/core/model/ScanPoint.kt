package com.kayser.areascan.core.model

/**
 * Tek bir manyetometre ölçüm noktasını temsil eder.
 *
 * @param x Tarama alanındaki yerel X koordinatı (metre)
 * @param y Tarama alanındaki yerel Y koordinatı (metre)
 * @param z Derinlik/Z koordinatı — yüzey taramasında genelde 0, B-Scan/4D modunda zaman/derinlik eksenine karşılık gelir
 * @param magnitude Manyetik alan büyüklüğü (µT) — kalibrasyon uygulanmış ham değer
 * @param rawX Kalibrasyon öncesi ham X ekseni manyetik alan değeri (µT)
 * @param rawY Kalibrasyon öncesi ham Y ekseni manyetik alan değeri (µT)
 * @param rawZ Kalibrasyon öncesi ham Z ekseni manyetik alan değeri (µT)
 * @param timestampNanos Sensör event zaman damgası (SensorEvent.timestamp, nanosaniye, monotonic clock)
 * @param accuracy SensorEvent.accuracy değeri (-1 .. 3 arası, kalibre edilmemiş sensörlerde anlamı sınırlıdır)
 */
data class ScanPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val magnitude: Float,
    val rawX: Float,
    val rawY: Float,
    val rawZ: Float,
    val timestampNanos: Long,
    val accuracy: Int = -1
) {
    /** Toprak/zemin temel seviyesi çıkarılmış normalize değer (FragmentSoilProperties karşılığı uygulanınca doldurulur). */
    var normalizedValue: Float? = null

    /** Bu nokta sınırlandırma kutusu (bounding box) içinde mi? UI tarafında filtreleme için kullanılır. */
    var insideBoundingBox: Boolean = true
}
