package com.example.render

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** iOS: EGL config is GLKView drawable settings; this only carries the MSAA wish to drshim.GlHost. */
class MsaaConfigChooser(private val es3: Boolean, private val allowMsaa: Boolean) : GLSurfaceView.EGLConfigChooser {
    var chosenSamples = if (allowMsaa) 4 else 0
        private set

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig? = null
    override val iosWantsMsaa: Boolean get() = allowMsaa
}
