package com.kayser.areascan.render.opengl

import com.kayser.areascan.core.model.Colormap

/**
 * Normalize edilmiş değer (0f..1f) -> RGB renk dönüşümü.
 * Vertex renklerini CPU tarafında hesaplamak için kullanılır (basit, taşınabilir yaklaşım;
 * ileride fragment shader'da hesaplanan bir 1D color-ramp texture'a geçilebilir).
 */
object ColormapPalette {

    fun colorFor(value: Float, colormap: Colormap): FloatArray {
        val v = value.coerceIn(0f, 1f)
        return when (colormap) {
            Colormap.GRAYSCALE -> floatArrayOf(v, v, v)
            Colormap.JET -> jet(v)
            Colormap.VIRIDIS -> viridisApprox(v)
            Colormap.RAINBOW -> rainbow(v)
            Colormap.THERMAL -> thermal(v)
        }
    }

    private fun jet(v: Float): FloatArray {
        val r = (1.5f - kotlin.math.abs(4f * v - 3f)).coerceIn(0f, 1f)
        val g = (1.5f - kotlin.math.abs(4f * v - 2f)).coerceIn(0f, 1f)
        val b = (1.5f - kotlin.math.abs(4f * v - 1f)).coerceIn(0f, 1f)
        return floatArrayOf(r, g, b)
    }

    private fun thermal(v: Float): FloatArray {
        // Siyah -> kırmızı -> sarı -> beyaz
        return when {
            v < 0.33f -> floatArrayOf(v / 0.33f, 0f, 0f)
            v < 0.66f -> floatArrayOf(1f, (v - 0.33f) / 0.33f, 0f)
            else -> floatArrayOf(1f, 1f, (v - 0.66f) / 0.34f)
        }
    }

    private fun rainbow(v: Float): FloatArray {
        val hue = v * 300f // mor->kırmızı arası tam tur değil, okunabilirlik için 300°
        return hsvToRgb(hue, 1f, 1f)
    }

    private fun viridisApprox(v: Float): FloatArray {
        // Tam viridis LUT yerine basit 4-nokta enterpolasyon yaklaşımı
        val stops = arrayOf(
            0.0f to floatArrayOf(0.267f, 0.005f, 0.329f),
            0.33f to floatArrayOf(0.128f, 0.567f, 0.551f),
            0.66f to floatArrayOf(0.478f, 0.821f, 0.318f),
            1.0f to floatArrayOf(0.993f, 0.906f, 0.144f)
        )
        for (i in 0 until stops.size - 1) {
            val (t0, c0) = stops[i]
            val (t1, c1) = stops[i + 1]
            if (v in t0..t1) {
                val t = (v - t0) / (t1 - t0)
                return floatArrayOf(
                    c0[0] + (c1[0] - c0[0]) * t,
                    c0[1] + (c1[1] - c0[1]) * t,
                    c0[2] + (c1[2] - c0[2]) * t
                )
            }
        }
        return stops.last().second
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return floatArrayOf(r + m, g + m, b + m)
    }
}
