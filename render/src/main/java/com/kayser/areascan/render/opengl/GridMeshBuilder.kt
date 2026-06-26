package com.kayser.areascan.render.opengl

import com.kayser.areascan.core.model.GridConfig
import com.kayser.areascan.core.model.ScanPoint
import com.kayser.areascan.core.model.SurfaceConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * [ScanPoint] listesinden GPU'ya yüklenebilir vertex/renk/indeks buffer'ları üretir.
 *
 * Yaklaşım: ham tarama noktaları düzenli bir ızgaraya denk gelmeyebilir (kullanıcı serbest
 * hareket ediyor olabilir), bu yüzden basit bir "en yakın hücreye yuvarla" (nearest-cell binning)
 * stratejisi kullanılır. Daha hassas sonuç için bu sınıf, RBF enterpolasyonu uygulanmış bir grid
 * de kabul edecek şekilde tasarlandı (bkz. [GridMeshBuilder.buildFromInterpolatedGrid]).
 */
class GridMeshBuilder {

    data class MeshData(
        val vertexBuffer: FloatBuffer,
        val colorBuffer: FloatBuffer,
        val indexBuffer: java.nio.ShortBuffer,
        val indexCount: Int,
        val vertexCount: Int
    )

    /** Ham, düzensiz tarama noktalarını binning ile bir ızgaraya oturtup mesh üretir. */
    fun buildFromScanPoints(
        points: List<ScanPoint>,
        grid: GridConfig,
        surface: SurfaceConfig
    ): MeshData? {
        if (points.isEmpty()) return null

        val res = grid.resolution
        val heightGrid = Array(res) { FloatArray(res) { Float.NaN } }
        val countGrid = Array(res) { IntArray(res) }

        for (p in points) {
            val gx = ((p.x / grid.widthMeters) * res).toInt().coerceIn(0, res - 1)
            val gy = ((p.y / grid.heightMeters) * res).toInt().coerceIn(0, res - 1)
            val value = p.normalizedValue ?: p.magnitude
            if (heightGrid[gy][gx].isNaN()) {
                heightGrid[gy][gx] = value
            } else {
                // Ortalama biriktirme (running average)
                val n = countGrid[gy][gx]
                heightGrid[gy][gx] = (heightGrid[gy][gx] * n + value) / (n + 1)
            }
            countGrid[gy][gx]++
        }

        return buildFromInterpolatedGrid(heightGrid, grid, surface)
    }

    /** RBF enterpolasyonu (veya başka bir kaynaktan) gelen tam dolu bir grid'den mesh üretir. */
    fun buildFromInterpolatedGrid(
        valueGrid: Array<FloatArray>,
        grid: GridConfig,
        surface: SurfaceConfig
    ): MeshData {
        val res = valueGrid.size

        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE
        for (row in valueGrid) for (v in row) {
            if (!v.isNaN()) {
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }
        }
        if (minVal == Float.MAX_VALUE) {
            minVal = 0f; maxVal = 1f
        }
        val range = (maxVal - minVal).takeIf { it > 1e-6f } ?: 1f

        val vertices = FloatArray(res * res * 3)
        val colors = FloatArray(res * res * 3)

        for (gy in 0 until res) {
            for (gx in 0 until res) {
                val idx = gy * res + gx
                val raw = valueGrid[gy][gx]
                val value = if (raw.isNaN()) minVal else raw
                val normalized = ((value - minVal) / range)

                val worldX = (gx.toFloat() / (res - 1)) * grid.widthMeters
                val worldY = (gy.toFloat() / (res - 1)) * grid.heightMeters
                val worldZ = normalized * surface.exaggerationFactor

                vertices[idx * 3] = worldX
                vertices[idx * 3 + 1] = worldZ // OpenGL'de Y yukarı eksen kabul edildi
                vertices[idx * 3 + 2] = worldY

                val color = ColormapPalette.colorFor(normalized, surface.colormap)
                colors[idx * 3] = color[0]
                colors[idx * 3 + 1] = color[1]
                colors[idx * 3 + 2] = color[2]
            }
        }

        // Triangle-strip yerine indexed triangle-list (her hücre için 2 üçgen)
        val indices = ShortArray((res - 1) * (res - 1) * 6)
        var ii = 0
        for (gy in 0 until res - 1) {
            for (gx in 0 until res - 1) {
                val topLeft = (gy * res + gx).toShort()
                val topRight = (gy * res + gx + 1).toShort()
                val bottomLeft = ((gy + 1) * res + gx).toShort()
                val bottomRight = ((gy + 1) * res + gx + 1).toShort()

                indices[ii++] = topLeft
                indices[ii++] = bottomLeft
                indices[ii++] = topRight

                indices[ii++] = topRight
                indices[ii++] = bottomLeft
                indices[ii++] = bottomRight
            }
        }

        return MeshData(
            vertexBuffer = toFloatBuffer(vertices),
            colorBuffer = toFloatBuffer(colors),
            indexBuffer = toShortBuffer(indices),
            indexCount = indices.size,
            vertexCount = res * res
        )
    }

    private fun toFloatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }

    private fun toShortBuffer(data: ShortArray): java.nio.ShortBuffer =
        ByteBuffer.allocateDirect(data.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(data); position(0) }
}
