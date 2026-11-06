package drshim

import android.opengl.GLSurfaceView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.EAGL.EAGLContext
import platform.EAGL.kEAGLRenderingAPIOpenGLES2
import platform.EAGL.kEAGLRenderingAPIOpenGLES3
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.GLKit.GLKView
import platform.GLKit.GLKViewDelegateProtocol
import platform.GLKit.GLKViewDrawableColorFormatRGBA8888
import platform.GLKit.GLKViewDrawableDepthFormat24
import platform.GLKit.GLKViewDrawableMultisample4X
import platform.GLKit.GLKViewDrawableMultisampleNone
import platform.GLKit.GLKViewDrawableStencilFormat8
import platform.QuartzCore.CADisplayLink
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.darwin.NSObject

/**
 * Owns the iOS GL surface: a GLKView behind the (transparent) Compose layer, driven by a
 * CADisplayLink on the main thread. The Android GameRenderer is called exactly as GLSurfaceView would.
 */
object GlHost {
    private var view: GLSurfaceView? = null
    private var glk: GLKView? = null
    private var link: CADisplayLink? = null
    private var created = false
    private var lastW = -1
    private var lastH = -1
    private var appActive = true
    private val pending = ArrayList<JRunnable>()

    /** Fraction of the native pixel density the 3D scene renders at (Compose HUD stays native). */
    var renderScale: Float = 1f
        set(v) {
            field = v.coerceIn(0.35f, 1f)
            applyScale()
        }
    var preferredFps: Int = 60
        set(v) {
            field = v
            link?.preferredFramesPerSecond = v.toLong()
        }

    var container: UIView? = null
        set(v) {
            field = v
            installIfReady()
        }

    /** Called when the GL thread would be told the frame time; the iOS root hooks perf telemetry here. */
    var onFrame: (() -> Unit)? = null

    fun attach(v: GLSurfaceView) {
        view = v
        installIfReady()
    }

    fun queue(r: JRunnable) {
        pending.add(r)
    }

    private val delegate = object : NSObject(), GLKViewDelegateProtocol {
        override fun glkView(view: GLKView, drawInRect: CValue<CGRect>) {
            drawFrame(view)
        }
    }

    private val ticker = Ticker()

    private class Ticker : NSObject() {
        @ObjCAction
        fun tick(sender: CADisplayLink) {
            if (!appActive) return
            glk?.display()
        }
    }

    private fun installIfReady() {
        val c = container ?: return
        val v = view ?: return
        if (glk != null) return
        val ctx = EAGLContext(kEAGLRenderingAPIOpenGLES3) ?: EAGLContext(kEAGLRenderingAPIOpenGLES2)
        val g = GLKView(frame = c.bounds, context = ctx!!)
        g.drawableColorFormat = GLKViewDrawableColorFormatRGBA8888
        g.drawableDepthFormat = GLKViewDrawableDepthFormat24
        g.drawableStencilFormat = GLKViewDrawableStencilFormat8
        g.drawableMultisample = if (v.wantsMsaa) GLKViewDrawableMultisample4X else GLKViewDrawableMultisampleNone
        g.enableSetNeedsDisplay = false
        g.userInteractionEnabled = false
        g.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        g.delegate = delegate
        c.insertSubview(g, atIndex = 0)
        glk = g
        applyScale()

        val l = CADisplayLink.displayLinkWithTarget(ticker, selector = NSSelectorFromString("tick:"))
        l.preferredFramesPerSecond = preferredFps.toLong()
        l.addToRunLoop(NSRunLoop.mainRunLoop, forMode = NSRunLoopCommonModes)
        link = l

        val nc = NSNotificationCenter.defaultCenter
        nc.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            appActive = false
            link?.paused = true
            // never touch GL in the background (iOS kills the app); flush what's queued now
            glk?.let { EAGLContext.setCurrentContext(it.context); android.opengl.GLES20.glFinish() }
        }
        nc.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            appActive = true
            link?.paused = false
        }
    }

    private fun applyScale() {
        val g = glk ?: return
        g.contentScaleFactor = UIScreen.mainScreen.nativeScale * renderScale
    }

    private fun drawFrame(g: GLKView) {
        val r = view?.renderer ?: return
        if (!created) {
            created = true
            r.onSurfaceCreated(null, null)
        }
        val w = g.drawableWidth.toInt()
        val h = g.drawableHeight.toInt()
        if (w != lastW || h != lastH) {
            lastW = w; lastH = h
            r.onSurfaceChanged(null, w, h)
        }
        if (pending.isNotEmpty()) {
            val q = pending.toList()
            pending.clear()
            q.forEach { runCatching { it.run() } }
        }
        try {
            r.onDrawFrame(null)
        } catch (t: Throwable) {
            println("GlHost: frame failed: $t")
        }
        onFrame?.invoke()
    }

    /** Pixel size of the screen's short side at native density (for RenderBudget.maxShortSidePx). */
    val nativeShortSidePx: Int
        get() = UIScreen.mainScreen.nativeBounds.useContents { minOf(size.width, size.height) }.toInt()
}
