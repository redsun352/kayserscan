package com.kayser.areascan.render.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Yüzey Tarama 3D görselleştirmesinin ana GLSurfaceView.Renderer implementasyonu.
 * Thuban Lodestar'daki ModelSurfaceView'in render mantığına karşılık gelir.
 */
class ModelSurfaceRenderer : GLSurfaceView.Renderer {

    val camera = OrbitCamera()
    private val meshRenderer = SurfaceMeshRenderer()

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var pendingMesh: GridMeshBuilder.MeshData? = null
    private var wireframeMode = false

    @Volatile
    private var meshDirty = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.06f, 0.07f, 0.09f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        meshRenderer.onSurfaceCreated()
        Matrix.setIdentityM(modelMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (meshDirty) {
            meshRenderer.setMesh(pendingMesh, wireframeMode)
            meshDirty = false
        }

        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, camera.computeViewMatrix(), 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        meshRenderer.draw(mvpMatrix)
    }

    /** UI/sensör thread'inden çağrılır; gerçek yükleme GL thread'inde onDrawFrame'de yapılır. */
    fun updateMesh(mesh: GridMeshBuilder.MeshData?, wireframe: Boolean) {
        pendingMesh = mesh
        wireframeMode = wireframe
        meshDirty = true
    }
}
