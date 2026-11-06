@file:Suppress("unused")

package android.opengl

import android.content.Context
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/** Stand-in for GLSurfaceView: the real surface is a GLKView owned by [drshim.GlHost]. */
open class GLSurfaceView(context: Context) : android.view.View(context) {
    interface Renderer {
        fun onSurfaceCreated(gl: GL10?, config: EGLConfig?)
        fun onSurfaceChanged(gl: GL10?, width: Int, height: Int)
        fun onDrawFrame(gl: GL10?)
    }

    interface EGLConfigChooser {
        fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig?
        /** iOS: whether the GLKView should use 4x multisampling. */
        val iosWantsMsaa: Boolean get() = true
    }

    var renderMode: Int = RENDERMODE_CONTINUOUSLY
    var preserveEGLContextOnPause: Boolean = true
    var debugFlags: Int = 0
    internal var renderer: Renderer? = null
    internal var wantsMsaa: Boolean = true

    fun setEGLContextClientVersion(version: Int) {}
    fun setEGLConfigChooser(chooser: EGLConfigChooser) { wantsMsaa = chooser.iosWantsMsaa }
    fun setEGLConfigChooser(r: Int, g: Int, b: Int, a: Int, depth: Int, stencil: Int) {}
    fun setRenderer(r: Renderer) {
        renderer = r
        drshim.GlHost.attach(this)
    }
    fun queueEvent(r: drshim.JRunnable) = drshim.GlHost.queue(r)
    fun requestRender() {}
    fun onPause() {}
    fun onResume() {}

    companion object {
        const val RENDERMODE_WHEN_DIRTY = 0
        const val RENDERMODE_CONTINUOUSLY = 1
        const val DEBUG_CHECK_GL_ERROR = 1
    }
}

object GLUtils
