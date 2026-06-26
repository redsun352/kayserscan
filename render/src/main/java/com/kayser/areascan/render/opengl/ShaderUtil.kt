package com.kayser.areascan.render.opengl

import android.opengl.GLES20
import android.util.Log

/**
 * GLSL shader derleme ve program linkleme yardımcıları.
 * Tüm render bileşenleri (Grid, Surface, Isosurface) bu sınıfı kullanır.
 */
object ShaderUtil {

    private const val TAG = "ShaderUtil"

    fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader derleme hatası: $log")
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader derlenemedi: $log")
        }
        return shader
    }

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Program link hatası: $log")
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Program linklenemedi: $log")
        }

        // Shader nesneleri programa attach edildikten sonra ayrı ayrı silinebilir.
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }
}
