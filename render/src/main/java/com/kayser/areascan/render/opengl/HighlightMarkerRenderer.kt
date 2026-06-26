package com.kayser.areascan.render.opengl

import android.opengl.GLES20

/**
 * Tarama modunda seçili (basılı tutulan) grid hücresini Y=0 düzleminde küçük bir
 * nokta/daire olarak vurgular. Performans için basit bir GL_TRIANGLE_FAN çemberi kullanılır.
 */
class HighlightMarkerRenderer {

    private var program = 0
    private var positionHandle = 0
    private var mvpMatrixHandle = 0
    private var colorHandle = 0

    private val circleVertices: java.nio.FloatBuffer
    private val segmentCount = 24

    init {
        val vertices = FloatArray((segmentCount + 2) * 3)
        // Merkez nokta
        vertices[0] = 0f; vertices[1] = 0f; vertices[2] = 0f
        for (i in 0..segmentCount) {
            val angle = (i.toFloat() / segmentCount) * 2f * Math.PI.toFloat()
            val idx = (i + 1) * 3
            vertices[idx] = kotlin.math.cos(angle) * RADIUS
            vertices[idx + 1] = 0f
            vertices[idx + 2] = kotlin.math.sin(angle) * RADIUS
        }
        circleVertices = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }
    }

    fun onSurfaceCreated() {
        program = ShaderUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
    }

    /**
     * @param worldXZ null ise hiçbir şey çizilmez (seçim yok)
     * @param mvpMatrix kameranın mevcut model-view-projection matrisi (translate öncesi)
     */
    fun draw(worldXZ: FloatArray?, mvpMatrix: FloatArray) {
        if (worldXZ == null) return

        val translatedMvp = FloatArray(16)
        val translation = FloatArray(16)
        android.opengl.Matrix.setIdentityM(translation, 0)
        android.opengl.Matrix.translateM(translation, 0, worldXZ[0], 0.02f, worldXZ[1])
        android.opengl.Matrix.multiplyMM(translatedMvp, 0, mvpMatrix, 0, translation, 0)

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        circleVertices.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, circleVertices)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, translatedMvp, 0)
        GLES20.glUniform4f(colorHandle, 0.79f, 0.64f, 0.15f, 0.85f) // kayser_secondary tonunda

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, segmentCount + 2)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    companion object {
        private const val RADIUS = 0.15f

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}
