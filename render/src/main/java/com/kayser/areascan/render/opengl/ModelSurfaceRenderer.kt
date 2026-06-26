package com.kayser.areascan.render.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLU
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
    private val highlightRenderer = HighlightMarkerRenderer()

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    // Dokunma (UI) thread'i ray-casting için bu anlık görüntüleri okur; GL thread'i
    // onDrawFrame içinde günceller. Diziler her güncellemede yeniden oluşturulduğu için
    // (referans ataması atomik) okuma sırasında yarım-yazılmış veri görülmez.
    @Volatile
    private var viewMatrixSnapshot: FloatArray = FloatArray(16)
    @Volatile
    private var projectionMatrixSnapshot: FloatArray = FloatArray(16)

    private var pendingMesh: GridMeshBuilder.MeshData? = null
    private var wireframeMode = false

    @Volatile
    private var meshDirty = false

    @Volatile
    private var viewportWidth = 1
    @Volatile
    private var viewportHeight = 1

    /** Tarama modunda seçili (vurgulanan) grid hücresinin dünya koordinatı; null = seçim yok. */
    @Volatile
    var highlightedCell: FloatArray? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.06f, 0.07f, 0.09f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        meshRenderer.onSurfaceCreated()
        highlightRenderer.onSurfaceCreated()
        Matrix.setIdentityM(modelMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
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

        val currentView = camera.computeViewMatrix()
        System.arraycopy(currentView, 0, viewMatrix, 0, 16)

        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        meshRenderer.draw(mvpMatrix)
        highlightRenderer.draw(highlightedCell, mvpMatrix)

        // Ray-casting için UI thread'e güvenle aktarılabilecek anlık görüntüler.
        // Yeni dizi oluşturmak (kopyalamak yerine) referans atamasının atomik olmasını sağlar.
        viewMatrixSnapshot = viewMatrix.copyOf()
        projectionMatrixSnapshot = projectionMatrix.copyOf()
    }

    /** UI/sensör thread'inden çağrılır; gerçek yükleme GL thread'inde onDrawFrame'de yapılır. */
    fun updateMesh(mesh: GridMeshBuilder.MeshData?, wireframe: Boolean) {
        pendingMesh = mesh
        wireframeMode = wireframe
        meshDirty = true
    }

    /**
     * Ekran koordinatını (piksel, sol-üst orijin) Y=0 dünya düzlemiyle kesiştirip
     * (worldX, worldZ) döndürür. GLU.gluUnProject ile near/far noktaları bulunup
     * aralarındaki doğru parçası Y=0 düzlemiyle kesiştirilir (ray-plane intersection).
     *
     * @return null eğer ışın düzleme paralel ise (pratikte top-down kamerada olmaz)
     */
    fun screenToWorldOnGroundPlane(screenX: Float, screenY: Float): FloatArray? {
        // OpenGL'in pencere koordinat sistemi alt-sol orijinlidir, Android dokunma
        // olayları üst-sol orijinlidir — Y eksenini çeviriyoruz.
        val glY = viewportHeight - screenY

        val nearPoint = FloatArray(4)
        val farPoint = FloatArray(4)

        val unprojectOk1 = GLU.gluUnProject(
            screenX, glY, 0f,
            viewMatrixSnapshot, 0, projectionMatrixSnapshot, 0,
            intArrayOf(0, 0, viewportWidth, viewportHeight), 0,
            nearPoint, 0
        )
        val unprojectOk2 = GLU.gluUnProject(
            screenX, glY, 1f,
            viewMatrixSnapshot, 0, projectionMatrixSnapshot, 0,
            intArrayOf(0, 0, viewportWidth, viewportHeight), 0,
            farPoint, 0
        )

        if (unprojectOk1 != GLU.GLU_TRUE || unprojectOk2 != GLU.GLU_TRUE) return null

        // w bölmesi (perspective divide)
        val nx = nearPoint[0] / nearPoint[3]
        val ny = nearPoint[1] / nearPoint[3]
        val nz = nearPoint[2] / nearPoint[3]
        val fx = farPoint[0] / farPoint[3]
        val fy = farPoint[1] / farPoint[3]
        val fz = farPoint[2] / farPoint[3]

        val dx = fx - nx
        val dy = fy - ny
        val dz = fz - nz

        // Y=0 düzlemiyle kesişim: ny + t*dy = 0  =>  t = -ny/dy
        if (kotlin.math.abs(dy) < 1e-6f) return null // ışın düzleme paralel
        val t = -ny / dy
        if (t < 0f) return null // kesişim kameranın arkasında

        val worldX = nx + t * dx
        val worldZ = nz + t * dz
        return floatArrayOf(worldX, worldZ)
    }
}
