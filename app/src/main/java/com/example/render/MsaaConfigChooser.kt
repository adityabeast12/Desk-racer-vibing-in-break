package com.example.render

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * v7: 4x MSAA + 24-bit depth when the device offers it, falling back step by step to the old
 * RGBA8888 / 16-bit depth config. MSAA removes the stair-step edges on car silhouettes, kerbs and
 * props (biggest single visual upgrade for low-poly art); 24-bit depth fixes distant z-fighting
 * with the 0.5..1400 depth range. Tile-based mobile GPUs resolve MSAA on-chip, so it's cheap.
 */
class MsaaConfigChooser(private val es3: Boolean, private val allowMsaa: Boolean) : GLSurfaceView.EGLConfigChooser {

    var chosenSamples = 0
        private set

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        // [depth, samples, stencil] — stencil 8 lets the renderer draw clean projected shadows
        val attempts = buildList {
            if (allowMsaa) {
                add(intArrayOf(24, 4, 8))
                add(intArrayOf(24, 4, 0))
                add(intArrayOf(16, 4, 0))
            }
            add(intArrayOf(24, 0, 8))
            add(intArrayOf(24, 0, 0))
            add(intArrayOf(16, 0, 0))
        }
        val renderables = if (es3) listOf(0x40 /* EGL_OPENGL_ES3_BIT_KHR */, 4) else listOf(4 /* EGL_OPENGL_ES2_BIT */)
        for (renderable in renderables) {
            for (a in attempts) {
                val depth = a[0]; val samples = a[1]; val stencil = a[2]
                val cfg = find(egl, display, depth, samples, renderable, stencil)
                if (cfg != null) {
                    chosenSamples = samples
                    return cfg
                }
            }
        }
        // last resort: anything with a depth buffer
        return find(egl, display, 16, 0, 4, 0) ?: throw IllegalArgumentException("No usable EGL config")
    }

    private fun find(egl: EGL10, display: EGLDisplay, depth: Int, samples: Int, renderable: Int, stencil: Int = 0): EGLConfig? {
        val attrs = ArrayList<Int>()
        attrs += listOf(
            EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_STENCIL_SIZE, stencil,
            EGL10.EGL_RENDERABLE_TYPE, renderable
        )
        if (samples > 0) attrs += listOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples)
        attrs += EGL10.EGL_NONE
        val spec = attrs.toIntArray()
        val num = IntArray(1)
        if (!egl.eglChooseConfig(display, spec, null, 0, num) || num[0] <= 0) return null
        val configs = arrayOfNulls<EGLConfig>(num[0])
        if (!egl.eglChooseConfig(display, spec, configs, num[0], num)) return null
        // prefer an exact 8888 match (some drivers list 10-bit / 565 configs first)
        val v = IntArray(1)
        fun attr(c: EGLConfig, a: Int): Int = if (egl.eglGetConfigAttrib(display, c, a, v)) v[0] else 0
        return configs.filterNotNull().firstOrNull {
            attr(it, EGL10.EGL_RED_SIZE) == 8 && attr(it, EGL10.EGL_GREEN_SIZE) == 8 &&
                attr(it, EGL10.EGL_BLUE_SIZE) == 8 && attr(it, EGL10.EGL_ALPHA_SIZE) == 8 &&
                attr(it, EGL10.EGL_DEPTH_SIZE) >= depth && attr(it, EGL10.EGL_STENCIL_SIZE) >= stencil
        } ?: configs.firstOrNull()
    }
}
