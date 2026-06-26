package com.kayser.areascan.render.marchingcubes

import com.kayser.areascan.core.model.MarchingCubesConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Marching Cubes algoritmasının ana implementasyonu.
 *
 * Girdi: 3 boyutlu bir scalar field (örn. enterpolasyon edilmiş manyetik anomali yoğunluğu).
 * Çıktı: belirtilen iso-değerine karşılık gelen isosurface'in üçgen mesh'i.
 *
 * Bu, Thuban Lodestar'daki "Hacim Ayarları" (FragmentMarchingCubesProperties) panelinin
 * karşılığı olan görselleştirme algoritmasıdır — ArkeoSAR ekosisteminde "isosurface/volumetrik
 * görünüm" olarak adlandırdığın özelliğin matematiksel temelidir.
 */
class MarchingCubesAlgorithm {

    data class IsoMeshData(
        val vertexBuffer: FloatBuffer,
        val normalBuffer: FloatBuffer,
        val vertexCount: Int
    )

    /**
     * @param field [x][y][z] sıralı 3B scalar değer dizisi (örn. normalize manyetik yoğunluk)
     * @param config marching cubes ayarları (iso seviyesi, voxel çözünürlüğü zaten field boyutunda yansır)
     * @param cellSize her voxel hücresinin gerçek dünya boyutu (metre), tek tip varsayıldı
     */
    fun extractIsosurface(
        field: Array<Array<FloatArray>>,
        config: MarchingCubesConfig,
        cellSize: Float = 0.1f
    ): IsoMeshData {
        val nx = field.size
        val ny = if (nx > 0) field[0].size else 0
        val nz = if (ny > 0) field[0][0].size else 0

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()

        if (nx < 2 || ny < 2 || nz < 2) {
            return IsoMeshData(emptyFloatBuffer(), emptyFloatBuffer(), 0)
        }

        val isoLevel = config.isoLevel

        for (xi in 0 until nx - 1) {
            for (yi in 0 until ny - 1) {
                for (zi in 0 until nz - 1) {
                    processCube(field, xi, yi, zi, isoLevel, cellSize, vertices, normals)
                }
            }
        }

        val vertexArray = vertices.toFloatArray()
        val normalArray = if (config.smoothNormals) {
            normals.toFloatArray()
        } else {
            computeFlatNormals(vertexArray)
        }

        return IsoMeshData(
            vertexBuffer = toFloatBuffer(vertexArray),
            normalBuffer = toFloatBuffer(normalArray),
            vertexCount = vertexArray.size / 3
        )
    }

    private fun processCube(
        field: Array<Array<FloatArray>>,
        xi: Int, yi: Int, zi: Int,
        isoLevel: Float,
        cellSize: Float,
        outVertices: MutableList<Float>,
        outNormals: MutableList<Float>
    ) {
        val cornerValues = FloatArray(8)
        val cornerPositions = Array(8) { FloatArray(3) }

        for (c in 0 until 8) {
            val (ox, oy, oz) = MarchingCubesTables.cornerOffsets[c].let { Triple(it[0], it[1], it[2]) }
            val cx = xi + ox
            val cy = yi + oy
            val cz = zi + oz
            cornerValues[c] = field[cx][cy][cz]
            cornerPositions[c] = floatArrayOf(cx * cellSize, cy * cellSize, cz * cellSize)
        }

        var cubeIndex = 0
        for (c in 0 until 8) {
            if (cornerValues[c] < isoLevel) {
                cubeIndex = cubeIndex or (1 shl c)
            }
        }

        val edgeMask = MarchingCubesTables.edgeTable[cubeIndex]
        if (edgeMask == 0) return

        // Her kesişen kenar için interpolasyon noktası hesapla
        val edgeVertices = arrayOfNulls<FloatArray>(12)
        for (e in 0 until 12) {
            if ((edgeMask and (1 shl e)) != 0) {
                val (v0, v1) = MarchingCubesTables.edgeVertexIndices[e].let { it[0] to it[1] }
                edgeVertices[e] = interpolateVertex(
                    cornerPositions[v0], cornerPositions[v1],
                    cornerValues[v0], cornerValues[v1],
                    isoLevel
                )
            }
        }

        val tri = MarchingCubesTables.triTable[cubeIndex]
        var i = 0
        while (i < tri.size && tri[i] != -1) {
            val p0 = edgeVertices[tri[i]] ?: break
            val p1 = edgeVertices[tri[i + 1]] ?: break
            val p2 = edgeVertices[tri[i + 2]] ?: break

            outVertices.addAll(listOf(p0[0], p0[1], p0[2]))
            outVertices.addAll(listOf(p1[0], p1[1], p1[2]))
            outVertices.addAll(listOf(p2[0], p2[1], p2[2]))

            val normal = computeFaceNormal(p0, p1, p2)
            repeat(3) { outNormals.addAll(listOf(normal[0], normal[1], normal[2])) }

            i += 3
        }
    }

    private fun interpolateVertex(
        p0: FloatArray, p1: FloatArray,
        v0: Float, v1: Float,
        isoLevel: Float
    ): FloatArray {
        if (kotlin.math.abs(isoLevel - v0) < 1e-6f) return p0
        if (kotlin.math.abs(isoLevel - v1) < 1e-6f) return p1
        if (kotlin.math.abs(v1 - v0) < 1e-6f) return p0

        val t = (isoLevel - v0) / (v1 - v0)
        return floatArrayOf(
            p0[0] + t * (p1[0] - p0[0]),
            p0[1] + t * (p1[1] - p0[1]),
            p0[2] + t * (p1[2] - p0[2])
        )
    }

    private fun computeFaceNormal(p0: FloatArray, p1: FloatArray, p2: FloatArray): FloatArray {
        val ux = p1[0] - p0[0]; val uy = p1[1] - p0[1]; val uz = p1[2] - p0[2]
        val vx = p2[0] - p0[0]; val vy = p2[1] - p0[1]; val vz = p2[2] - p0[2]

        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx

        val length = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 1e-9f } ?: 1f
        return floatArrayOf(nx / length, ny / length, nz / length)
    }

    private fun computeFlatNormals(vertices: FloatArray): FloatArray {
        val normals = FloatArray(vertices.size)
        var i = 0
        while (i < vertices.size) {
            val p0 = floatArrayOf(vertices[i], vertices[i + 1], vertices[i + 2])
            val p1 = floatArrayOf(vertices[i + 3], vertices[i + 4], vertices[i + 5])
            val p2 = floatArrayOf(vertices[i + 6], vertices[i + 7], vertices[i + 8])
            val n = computeFaceNormal(p0, p1, p2)
            for (k in 0 until 3) {
                normals[i + k * 3] = n[0]
                normals[i + k * 3 + 1] = n[1]
                normals[i + k * 3 + 2] = n[2]
            }
            i += 9
        }
        return normals
    }

    private fun toFloatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }

    private fun emptyFloatBuffer(): FloatBuffer = toFloatBuffer(FloatArray(0))
}
