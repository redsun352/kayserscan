package com.kayser.areascan.render.opengl

import android.opengl.GLES20
import android.opengl.Matrix

/**
 * Tek bir grid/yüzey mesh'ini ekrana çizen düşük seviyeli renderer.
 * Kamera matrisleri [ModelSurfaceRenderer] tarafından sağlanır; bu sınıf sadece
 * mevcut mesh verisini GPU'ya yükleyip draw call yapmakla ilgilenir.
 */
class SurfaceMeshRenderer {

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    private var wireframeHandle = 0

    private var currentMesh: GridMeshBuilder.MeshData? = null
    private var wireframe = false

    fun onSurfaceCreated() {
        program = ShaderUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "vColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    fun setMesh(mesh: GridMeshBuilder.MeshData?, wireframe: Boolean) {
        this.currentMesh = mesh
        this.wireframe = wireframe
    }

    /** Debug/log amaçlı: şu an çizilecek bir mesh var mı. */
    fun hasMesh(): Boolean = currentMesh != null

    fun draw(mvpMatrix: FloatArray) {
        val mesh = currentMesh ?: return

        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(positionHandle)
        mesh.vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.vertexBuffer)

        GLES20.glEnableVertexAttribArray(colorHandle)
        mesh.colorBuffer.position(0)
        GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.colorBuffer)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        mesh.indexBuffer.position(0)
        val mode = if (wireframe) GLES20.GL_LINES else GLES20.GL_TRIANGLES
        GLES20.glDrawElements(mode, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            attribute vec3 vColor;
            varying vec3 fColor;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                fColor = vColor;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec3 fColor;
            void main() {
                gl_FragColor = vec4(fColor, 1.0);
            }
        """
    }
}

/** Kamera projeksiyon/view matrislerini ve dokunma ile rotasyon/zoom kontrolünü yönetir. */
class OrbitCamera {
    var rotationX = 30f
    var rotationY = 0f
    var distance = 8f
        private set

    /** Tarama modunda true olduğunda rotate()/zoom() çağrıları yok sayılır (kamera kilitli). */
    var topDownLocked = false
        private set

    private val minDistance = 1.5f
    private val maxDistance = 50f

    fun rotate(deltaX: Float, deltaY: Float) {
        if (topDownLocked) return
        rotationY = (rotationY + deltaX) % 360f
        rotationX = (rotationX + deltaY).coerceIn(-89f, 89f)
    }

    fun zoom(factor: Float) {
        if (topDownLocked) return
        distance = (distance / factor).coerceIn(minDistance, maxDistance)
    }

    fun resetRotation() {
        rotationX = 30f
        rotationY = 0f
    }

    fun resetZoom() {
        distance = 8f
    }

    /**
     * Tarama (dokunmatik ölçüm) modu için kamerayı tam yukarıdan bakan, sabit konuma kilitler.
     * 90° tam dikine bakış gimbal lock'a yol açabileceğinden 89.9° kullanılır.
     */
    fun lockTopDown(viewDistance: Float = 10f) {
        rotationX = 89.9f
        rotationY = 0f
        distance = viewDistance
        topDownLocked = true
    }

    fun unlockOrbit() {
        topDownLocked = false
    }

    fun computeViewMatrix(target: FloatArray = floatArrayOf(2.5f, 0f, 2.5f)): FloatArray {
        val viewMatrix = FloatArray(16)
        val radX = Math.toRadians(rotationX.toDouble())
        val radY = Math.toRadians(rotationY.toDouble())

        val eyeX = target[0] + (distance * Math.cos(radX) * Math.sin(radY)).toFloat()
        val eyeY = target[1] + (distance * Math.sin(radX)).toFloat()
        val eyeZ = target[2] + (distance * Math.cos(radX) * Math.cos(radY)).toFloat()

        // Top-down kilitli modda bakış yönü (0,-1,0)'a çok yaklaşır; (0,1,0) up vektörü
        // bu durumda sayısal kararsızlığa yol açabilir. Bunun yerine dünya Z eksenini
        // "yukarı" kabul ederek ekranda tutarlı bir kuzey-yukarı yönelim sağlanır.
        val upX: Float
        val upY: Float
        val upZ: Float
        if (topDownLocked) {
            upX = 0f; upY = 0f; upZ = -1f
        } else {
            upX = 0f; upY = 1f; upZ = 0f
        }

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            target[0], target[1], target[2],
            upX, upY, upZ
        )
        return viewMatrix
    }
}
