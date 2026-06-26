package com.kayser.areascan.core.interpolation

import com.kayser.areascan.core.model.RbfConfig
import com.kayser.areascan.core.model.RbfKernel
import com.kayser.areascan.core.model.ScanPoint

/**
 * Radial Basis Function (RBF) enterpolasyonu: düzensiz/seyrek dağılmış tarama noktalarından
 * düzenli bir ızgara üzerinde sürekli bir yüzey tahmin eder.
 *
 * Bu sınıf, Hasan'ın `util/rbf_interp` / RBF_Types çalışmasındaki kavramsal yaklaşımla
 * aynı matematiksel temele dayanır: φ(r) çekirdek fonksiyonu + ağırlık çözümü (lineer sistem).
 *
 * Performans notu: N nokta için O(N^3) lineer sistem çözümü gerekir (Gauss eliminasyonu burada
 * kullanıldı). Saha taramalarında nokta sayısı çoksa (>500), önceden bir alt örnekleme veya
 * yerel/parçalı RBF (compact support) stratejisi düşünülmelidir — şimdilik basit/doğru versiyon.
 */
class RbfInterpolator {

    fun interpolate(
        points: List<ScanPoint>,
        config: RbfConfig,
        gridResolution: Int,
        domainWidth: Float,
        domainHeight: Float
    ): Array<FloatArray> {
        val n = points.size
        if (n == 0) {
            return Array(gridResolution) { FloatArray(gridResolution) { 0f } }
        }
        if (n == 1) {
            val v = points[0].normalizedValue ?: points[0].magnitude
            return Array(gridResolution) { FloatArray(gridResolution) { v } }
        }

        val xs = DoubleArray(n) { points[it].x.toDouble() }
        val ys = DoubleArray(n) { points[it].y.toDouble() }
        val values = DoubleArray(n) { (points[it].normalizedValue ?: points[it].magnitude).toDouble() }

        // A * w = values  (A[i][j] = phi(|p_i - p_j|))
        val a = Array(n) { i -> DoubleArray(n) { j -> kernel(distance(xs[i], ys[i], xs[j], ys[j]), config) } }

        // Smoothing: diagonal'a küçük bir değer ekleyerek sayısal stabilite + düzleştirme sağlanır
        if (config.smoothing > 0f) {
            for (i in 0 until n) a[i][i] += config.smoothing.toDouble()
        }

        val weights = solveLinearSystem(a, values) ?: run {
            // Tekil (singular) sistem durumunda basit ortalama ile fallback
            val avg = values.average()
            return Array(gridResolution) { FloatArray(gridResolution) { avg.toFloat() } }
        }

        val output = Array(gridResolution) { FloatArray(gridResolution) }
        for (gy in 0 until gridResolution) {
            for (gx in 0 until gridResolution) {
                val px = (gx.toDouble() / (gridResolution - 1)) * domainWidth
                val py = (gy.toDouble() / (gridResolution - 1)) * domainHeight
                var sum = 0.0
                for (i in 0 until n) {
                    sum += weights[i] * kernel(distance(px, py, xs[i], ys[i]), config)
                }
                output[gy][gx] = sum.toFloat()
            }
        }
        return output
    }

    private fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun kernel(r: Double, config: RbfConfig): Double {
        val eps = config.epsilon.toDouble()
        return when (config.kernel) {
            RbfKernel.LINEAR -> r
            RbfKernel.CUBIC -> r * r * r
            RbfKernel.THIN_PLATE_SPLINE -> if (r < 1e-9) 0.0 else r * r * kotlin.math.ln(r)
            RbfKernel.GAUSSIAN -> kotlin.math.exp(-(eps * r) * (eps * r))
            RbfKernel.MULTIQUADRIC -> kotlin.math.sqrt(1.0 + (eps * r) * (eps * r))
            RbfKernel.INVERSE_MULTIQUADRIC -> 1.0 / kotlin.math.sqrt(1.0 + (eps * r) * (eps * r))
        }
    }

    /** Gauss eliminasyonu (kısmi pivotlama ile) — n küçük/orta (saha taramasında tipik <300) olduğu için yeterli. */
    private fun solveLinearSystem(aIn: Array<DoubleArray>, bIn: DoubleArray): DoubleArray? {
        val n = bIn.size
        val a = Array(n) { aIn[it].copyOf() }
        val b = bIn.copyOf()

        for (col in 0 until n) {
            var pivotRow = col
            var maxVal = kotlin.math.abs(a[col][col])
            for (row in col + 1 until n) {
                if (kotlin.math.abs(a[row][col]) > maxVal) {
                    maxVal = kotlin.math.abs(a[row][col])
                    pivotRow = row
                }
            }
            if (maxVal < 1e-12) return null // tekil matris

            if (pivotRow != col) {
                val tmpRow = a[col]; a[col] = a[pivotRow]; a[pivotRow] = tmpRow
                val tmpB = b[col]; b[col] = b[pivotRow]; b[pivotRow] = tmpB
            }

            for (row in col + 1 until n) {
                val factor = a[row][col] / a[col][col]
                for (k in col until n) a[row][k] -= factor * a[col][k]
                b[row] -= factor * b[col]
            }
        }

        val x = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var sum = b[row]
            for (k in row + 1 until n) sum -= a[row][k] * x[k]
            x[row] = sum / a[row][row]
        }
        return x
    }
}
