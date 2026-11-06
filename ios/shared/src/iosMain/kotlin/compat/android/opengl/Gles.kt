@file:Suppress("unused", "FunctionName")

package android.opengl

import java.nio.Buffer
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memcpy

/** android.opengl.GLES20 on top of iOS OpenGL ES (EAGL). Same int-based signatures as Android. */
open class GLES20Base {
    companion object
}

object GLES20 {
    const val GL_DEPTH_BUFFER_BIT = 0x00000100
    const val GL_STENCIL_BUFFER_BIT = 0x00000400
    const val GL_COLOR_BUFFER_BIT = 0x00004000
    const val GL_FALSE = 0
    const val GL_TRUE = 1
    const val GL_POINTS = 0x0000
    const val GL_LINES = 0x0001
    const val GL_LINE_LOOP = 0x0002
    const val GL_LINE_STRIP = 0x0003
    const val GL_TRIANGLES = 0x0004
    const val GL_TRIANGLE_STRIP = 0x0005
    const val GL_TRIANGLE_FAN = 0x0006
    const val GL_ZERO = 0
    const val GL_ONE = 1
    const val GL_SRC_COLOR = 0x0300
    const val GL_ONE_MINUS_SRC_COLOR = 0x0301
    const val GL_SRC_ALPHA = 0x0302
    const val GL_ONE_MINUS_SRC_ALPHA = 0x0303
    const val GL_DST_ALPHA = 0x0304
    const val GL_ONE_MINUS_DST_ALPHA = 0x0305
    const val GL_DST_COLOR = 0x0306
    const val GL_ONE_MINUS_DST_COLOR = 0x0307
    const val GL_FUNC_ADD = 0x8006
    const val GL_FRONT = 0x0404
    const val GL_BACK = 0x0405
    const val GL_FRONT_AND_BACK = 0x0408
    const val GL_TEXTURE_2D = 0x0DE1
    const val GL_CULL_FACE = 0x0B44
    const val GL_BLEND = 0x0BE2
    const val GL_DITHER = 0x0BD0
    const val GL_STENCIL_TEST = 0x0B90
    const val GL_DEPTH_TEST = 0x0B71
    const val GL_SCISSOR_TEST = 0x0C11
    const val GL_POLYGON_OFFSET_FILL = 0x8037
    const val GL_SAMPLE_ALPHA_TO_COVERAGE = 0x809E
    const val GL_NO_ERROR = 0
    const val GL_INVALID_ENUM = 0x0500
    const val GL_INVALID_VALUE = 0x0501
    const val GL_INVALID_OPERATION = 0x0502
    const val GL_OUT_OF_MEMORY = 0x0505
    const val GL_CW = 0x0900
    const val GL_CCW = 0x0901
    const val GL_LINE_WIDTH = 0x0B21
    const val GL_VIEWPORT = 0x0BA2
    const val GL_MAX_TEXTURE_SIZE = 0x0D33
    const val GL_STENCIL_BITS = 0x0D57
    const val GL_DEPTH_BITS = 0x0D56
    const val GL_UNPACK_ALIGNMENT = 0x0CF5
    const val GL_PACK_ALIGNMENT = 0x0D05
    const val GL_BYTE = 0x1400
    const val GL_UNSIGNED_BYTE = 0x1401
    const val GL_SHORT = 0x1402
    const val GL_UNSIGNED_SHORT = 0x1403
    const val GL_INT = 0x1404
    const val GL_UNSIGNED_INT = 0x1405
    const val GL_FLOAT = 0x1406
    const val GL_DEPTH_COMPONENT = 0x1902
    const val GL_ALPHA = 0x1906
    const val GL_RGB = 0x1907
    const val GL_RGBA = 0x1908
    const val GL_LUMINANCE = 0x1909
    const val GL_LUMINANCE_ALPHA = 0x190A
    const val GL_UNSIGNED_SHORT_5_6_5 = 0x8363
    const val GL_FRAGMENT_SHADER = 0x8B30
    const val GL_VERTEX_SHADER = 0x8B31
    const val GL_COMPILE_STATUS = 0x8B81
    const val GL_LINK_STATUS = 0x8B82
    const val GL_INFO_LOG_LENGTH = 0x8B84
    const val GL_NEVER = 0x0200
    const val GL_LESS = 0x0201
    const val GL_EQUAL = 0x0202
    const val GL_LEQUAL = 0x0203
    const val GL_GREATER = 0x0204
    const val GL_NOTEQUAL = 0x0205
    const val GL_GEQUAL = 0x0206
    const val GL_ALWAYS = 0x0207
    const val GL_KEEP = 0x1E00
    const val GL_REPLACE = 0x1E01
    const val GL_INCR = 0x1E02
    const val GL_DECR = 0x1E03
    const val GL_INVERT = 0x150A
    const val GL_INCR_WRAP = 0x8507
    const val GL_DECR_WRAP = 0x8508
    const val GL_VENDOR = 0x1F00
    const val GL_RENDERER = 0x1F01
    const val GL_VERSION = 0x1F02
    const val GL_EXTENSIONS = 0x1F03
    const val GL_SHADING_LANGUAGE_VERSION = 0x8B8C
    const val GL_NEAREST = 0x2600
    const val GL_LINEAR = 0x2601
    const val GL_NEAREST_MIPMAP_NEAREST = 0x2700
    const val GL_LINEAR_MIPMAP_NEAREST = 0x2701
    const val GL_NEAREST_MIPMAP_LINEAR = 0x2702
    const val GL_LINEAR_MIPMAP_LINEAR = 0x2703
    const val GL_TEXTURE_MAG_FILTER = 0x2800
    const val GL_TEXTURE_MIN_FILTER = 0x2801
    const val GL_TEXTURE_WRAP_S = 0x2802
    const val GL_TEXTURE_WRAP_T = 0x2803
    const val GL_TEXTURE = 0x1702
    const val GL_TEXTURE_CUBE_MAP = 0x8513
    const val GL_TEXTURE0 = 0x84C0
    const val GL_TEXTURE1 = 0x84C1
    const val GL_TEXTURE2 = 0x84C2
    const val GL_TEXTURE3 = 0x84C3
    const val GL_TEXTURE4 = 0x84C4
    const val GL_TEXTURE5 = 0x84C5
    const val GL_TEXTURE6 = 0x84C6
    const val GL_TEXTURE7 = 0x84C7
    const val GL_REPEAT = 0x2901
    const val GL_CLAMP_TO_EDGE = 0x812F
    const val GL_MIRRORED_REPEAT = 0x8370
    const val GL_ARRAY_BUFFER = 0x8892
    const val GL_ELEMENT_ARRAY_BUFFER = 0x8893
    const val GL_STREAM_DRAW = 0x88E0
    const val GL_STATIC_DRAW = 0x88E4
    const val GL_DYNAMIC_DRAW = 0x88E8
    const val GL_FRAMEBUFFER = 0x8D40
    const val GL_RENDERBUFFER = 0x8D41
    const val GL_COLOR_ATTACHMENT0 = 0x8CE0
    const val GL_DEPTH_ATTACHMENT = 0x8D00
    const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
    const val GL_DEPTH_COMPONENT16 = 0x81A5

    private fun b(v: Boolean): UByte = if (v) 1u else 0u

    // Apple's GLES3 doesn't reliably build mip chains for GL_SRGB8_ALPHA8 (distant texels came out
    // black), so sRGB level-0 uploads are remembered and their mips are box-filtered on the CPU.
    private var activeUnit = 0
    private val bound2D = IntArray(32)
    private class PendingMip(val w: Int, val h: Int, val px: ByteArray)
    private val pendingSrgb = HashMap<Int, PendingMip>()
    private const val SRGB8_ALPHA8 = 0x8C43

    private fun buildMips(tex: Int, m: PendingMip) {
        var w = m.w
        var h = m.h
        var src = m.px
        var level = 0
        while (w > 1 || h > 1) {
            val nw = maxOf(1, w / 2)
            val nh = maxOf(1, h / 2)
            val dst = ByteArray(nw * nh * 4)
            for (y in 0 until nh) {
                val y0 = minOf(h - 1, y * 2)
                val y1 = minOf(h - 1, y * 2 + 1)
                for (x in 0 until nw) {
                    val x0 = minOf(w - 1, x * 2)
                    val x1 = minOf(w - 1, x * 2 + 1)
                    val a = (y0 * w + x0) * 4
                    val bb = (y0 * w + x1) * 4
                    val c = (y1 * w + x0) * 4
                    val d = (y1 * w + x1) * 4
                    val o = (y * nw + x) * 4
                    for (k in 0 until 4) {
                        val sum = (src[a + k].toInt() and 0xFF) + (src[bb + k].toInt() and 0xFF) +
                            (src[c + k].toInt() and 0xFF) + (src[d + k].toInt() and 0xFF)
                        dst[o + k] = ((sum + 2) / 4).toByte()
                    }
                }
            }
            level++
            dst.usePinned {
                platform.gles3.glTexImage2D(GL_TEXTURE_2D.convert(), level, SRGB8_ALPHA8, nw, nh, 0, GL_RGBA.convert(), GL_UNSIGNED_BYTE.convert(), it.addressOf(0))
            }
            src = dst; w = nw; h = nh
        }
        platform.gles3.glTexParameteri(GL_TEXTURE_2D.convert(), 0x813D.convert(), level) // GL_TEXTURE_MAX_LEVEL
    }

    fun glActiveTexture(texture: Int) {
        activeUnit = (texture - GL_TEXTURE0).coerceIn(0, 31)
        platform.gles3.glActiveTexture(texture.convert())
    }
    fun glAttachShader(program: Int, shader: Int) = platform.gles3.glAttachShader(program.convert(), shader.convert())
    fun glBindAttribLocation(program: Int, index: Int, name: String) = platform.gles3.glBindAttribLocation(program.convert(), index.convert(), name)
    fun glBindBuffer(target: Int, buffer: Int) = platform.gles3.glBindBuffer(target.convert(), buffer.convert())
    fun glBindFramebuffer(target: Int, fb: Int) = platform.gles3.glBindFramebuffer(target.convert(), fb.convert())
    fun glBindTexture(target: Int, texture: Int) {
        if (target == GL_TEXTURE_2D) bound2D[activeUnit] = texture
        platform.gles3.glBindTexture(target.convert(), texture.convert())
    }
    fun glBlendFunc(s: Int, d: Int) = platform.gles3.glBlendFunc(s.convert(), d.convert())
    fun glBlendFuncSeparate(a: Int, b: Int, c: Int, d: Int) = platform.gles3.glBlendFuncSeparate(a.convert(), b.convert(), c.convert(), d.convert())
    fun glBlendEquation(mode: Int) = platform.gles3.glBlendEquation(mode.convert())
    fun glClear(mask: Int) = platform.gles3.glClear(mask.convert())
    fun glClearColor(r: Float, g: Float, b: Float, a: Float) = platform.gles3.glClearColor(r, g, b, a)
    fun glClearDepthf(d: Float) = platform.gles3.glClearDepthf(d)
    fun glClearStencil(s: Int) = platform.gles3.glClearStencil(s)
    fun glColorMask(r: Boolean, g: Boolean, bl: Boolean, a: Boolean) = platform.gles3.glColorMask(b(r), b(g), b(bl), b(a))
    fun glCompileShader(shader: Int) = platform.gles3.glCompileShader(shader.convert())
    fun glCreateProgram(): Int = platform.gles3.glCreateProgram().toInt()
    fun glCreateShader(type: Int): Int = platform.gles3.glCreateShader(type.convert()).toInt()
    fun glCullFace(mode: Int) = platform.gles3.glCullFace(mode.convert())
    fun glDeleteProgram(p: Int) = platform.gles3.glDeleteProgram(p.convert())
    fun glDeleteShader(s: Int) = platform.gles3.glDeleteShader(s.convert())
    fun glDepthFunc(f: Int) = platform.gles3.glDepthFunc(f.convert())
    fun glDepthMask(flag: Boolean) = platform.gles3.glDepthMask(b(flag))
    fun glDisable(cap: Int) = platform.gles3.glDisable(cap.convert())
    fun glDisableVertexAttribArray(index: Int) {
        if (index >= 0) platform.gles3.glDisableVertexAttribArray(index.convert())
    }
    fun glDrawArrays(mode: Int, first: Int, count: Int) = platform.gles3.glDrawArrays(mode.convert(), first, count)
    fun glDrawElements(mode: Int, count: Int, type: Int, indices: Buffer) {
        indices.withAddress { p -> platform.gles3.glDrawElements(mode.convert(), count, type.convert(), p) }
    }
    fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) =
        platform.gles3.glDrawElements(mode.convert(), count, type.convert(), offset.toLong().toCPointer<ByteVar>())
    fun glEnable(cap: Int) = platform.gles3.glEnable(cap.convert())
    fun glEnableVertexAttribArray(index: Int) {
        if (index >= 0) platform.gles3.glEnableVertexAttribArray(index.convert())
    }
    fun glFinish() = platform.gles3.glFinish()
    fun glFlush() = platform.gles3.glFlush()
    fun glFrontFace(mode: Int) = platform.gles3.glFrontFace(mode.convert())
    fun glGenerateMipmap(target: Int) {
        val tex = bound2D[activeUnit]
        val m = if (target == GL_TEXTURE_2D) pendingSrgb.remove(tex) else null
        if (m != null) buildMips(tex, m) else platform.gles3.glGenerateMipmap(target.convert())
    }

    fun glGenTextures(n: Int, textures: IntArray, offset: Int) {
        textures.usePinned { platform.gles3.glGenTextures(n, it.addressOf(offset).reinterpret<UIntVar>()) }
    }
    fun glDeleteTextures(n: Int, textures: IntArray, offset: Int) {
        for (i in 0 until n) pendingSrgb.remove(textures[offset + i])
        textures.usePinned { platform.gles3.glDeleteTextures(n, it.addressOf(offset).reinterpret<UIntVar>()) }
    }
    fun glGenBuffers(n: Int, buffers: IntArray, offset: Int) {
        buffers.usePinned { platform.gles3.glGenBuffers(n, it.addressOf(offset).reinterpret<UIntVar>()) }
    }
    fun glDeleteBuffers(n: Int, buffers: IntArray, offset: Int) {
        buffers.usePinned { platform.gles3.glDeleteBuffers(n, it.addressOf(offset).reinterpret<UIntVar>()) }
    }
    fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int) {
        if (data == null) platform.gles3.glBufferData(target.convert(), size.convert(), null, usage.convert())
        else data.withAddress { p -> platform.gles3.glBufferData(target.convert(), size.convert(), p, usage.convert()) }
    }

    fun glGetAttribLocation(program: Int, name: String): Int = platform.gles3.glGetAttribLocation(program.convert(), name)
    fun glGetUniformLocation(program: Int, name: String): Int = platform.gles3.glGetUniformLocation(program.convert(), name)
    fun glGetError(): Int = platform.gles3.glGetError().toInt()

    fun glGetIntegerv(pname: Int, params: IntArray, offset: Int) {
        params.usePinned { platform.gles3.glGetIntegerv(pname.convert(), it.addressOf(offset)) }
    }
    fun glGetFloatv(pname: Int, params: FloatArray, offset: Int) {
        params.usePinned { platform.gles3.glGetFloatv(pname.convert(), it.addressOf(offset)) }
    }
    fun glGetShaderiv(shader: Int, pname: Int, params: IntArray, offset: Int) {
        params.usePinned { platform.gles3.glGetShaderiv(shader.convert(), pname.convert(), it.addressOf(offset)) }
    }
    fun glGetProgramiv(program: Int, pname: Int, params: IntArray, offset: Int) {
        params.usePinned { platform.gles3.glGetProgramiv(program.convert(), pname.convert(), it.addressOf(offset)) }
    }
    fun glGetShaderInfoLog(shader: Int): String = memScoped {
        val len = 4096
        val buf = allocArray<ByteVar>(len)
        val outLen = alloc<IntVar>()
        platform.gles3.glGetShaderInfoLog(shader.convert(), len, outLen.ptr, buf.reinterpret<ByteVar>())
        buf.toKString()
    }
    fun glGetProgramInfoLog(program: Int): String = memScoped {
        val len = 4096
        val buf = allocArray<ByteVar>(len)
        val outLen = alloc<IntVar>()
        platform.gles3.glGetProgramInfoLog(program.convert(), len, outLen.ptr, buf.reinterpret<ByteVar>())
        buf.toKString()
    }
    fun glGetString(name: Int): String? = platform.gles3.glGetString(name.convert())?.reinterpret<ByteVar>()?.toKString()

    fun glLineWidth(w: Float) = platform.gles3.glLineWidth(w)
    fun glLinkProgram(program: Int) = platform.gles3.glLinkProgram(program.convert())
    fun glPixelStorei(pname: Int, param: Int) = platform.gles3.glPixelStorei(pname.convert(), param)
    fun glPolygonOffset(factor: Float, units: Float) = platform.gles3.glPolygonOffset(factor, units)
    fun glReadPixels(x: Int, y: Int, width: Int, height: Int, format: Int, type: Int, pixels: Buffer) {
        pixels.withAddress { p -> platform.gles3.glReadPixels(x, y, width, height, format.convert(), type.convert(), p) }
    }
    fun glScissor(x: Int, y: Int, w: Int, h: Int) = platform.gles3.glScissor(x, y, w, h)

    fun glShaderSource(shader: Int, string: String) {
        memScoped {
            val src = alloc<CPointerVar<ByteVar>>()
            src.value = string.cstr.ptr
            platform.gles3.glShaderSource(shader.convert(), 1, src.ptr, null)
        }
    }

    fun glStencilFunc(func: Int, ref: Int, mask: Int) = platform.gles3.glStencilFunc(func.convert(), ref, mask.convert())
    fun glStencilMask(mask: Int) = platform.gles3.glStencilMask(mask.convert())
    fun glStencilOp(fail: Int, zfail: Int, zpass: Int) = platform.gles3.glStencilOp(fail.convert(), zfail.convert(), zpass.convert())

    fun glTexImage2D(target: Int, level: Int, internalformat: Int, width: Int, height: Int, border: Int, format: Int, type: Int, pixels: Buffer?) {
        if (pixels == null) {
            platform.gles3.glTexImage2D(target.convert(), level, internalformat, width, height, border, format.convert(), type.convert(), null)
        } else {
            pixels.withAddress { p ->
                platform.gles3.glTexImage2D(target.convert(), level, internalformat, width, height, border, format.convert(), type.convert(), p)
                if (target == GL_TEXTURE_2D && level == 0 && internalformat == SRGB8_ALPHA8 && format == GL_RGBA && type == GL_UNSIGNED_BYTE) {
                    val n = width * height * 4
                    val copy = ByteArray(n)
                    copy.usePinned { memcpy(it.addressOf(0), p, n.convert()) }
                    pendingSrgb[bound2D[activeUnit]] = PendingMip(width, height, copy)
                }
            }
        }
    }
    fun glTexSubImage2D(target: Int, level: Int, xoff: Int, yoff: Int, width: Int, height: Int, format: Int, type: Int, pixels: Buffer) {
        pixels.withAddress { p ->
            platform.gles3.glTexSubImage2D(target.convert(), level, xoff, yoff, width, height, format.convert(), type.convert(), p)
        }
    }
    fun glTexParameteri(target: Int, pname: Int, param: Int) = platform.gles3.glTexParameteri(target.convert(), pname.convert(), param)
    fun glTexParameterf(target: Int, pname: Int, param: Float) = platform.gles3.glTexParameterf(target.convert(), pname.convert(), param)

    fun glUniform1f(location: Int, x: Float) = platform.gles3.glUniform1f(location, x)
    fun glUniform2f(location: Int, x: Float, y: Float) = platform.gles3.glUniform2f(location, x, y)
    fun glUniform3f(location: Int, x: Float, y: Float, z: Float) = platform.gles3.glUniform3f(location, x, y, z)
    fun glUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) = platform.gles3.glUniform4f(location, x, y, z, w)
    fun glUniform1i(location: Int, x: Int) = platform.gles3.glUniform1i(location, x)
    fun glUniform1fv(location: Int, count: Int, v: FloatArray, offset: Int) {
        v.usePinned { platform.gles3.glUniform1fv(location, count, it.addressOf(offset)) }
    }
    fun glUniform3fv(location: Int, count: Int, v: FloatArray, offset: Int) {
        v.usePinned { platform.gles3.glUniform3fv(location, count, it.addressOf(offset)) }
    }
    fun glUniform4fv(location: Int, count: Int, v: FloatArray, offset: Int) {
        v.usePinned { platform.gles3.glUniform4fv(location, count, it.addressOf(offset)) }
    }
    fun glUniformMatrix3fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) {
        value.usePinned { platform.gles3.glUniformMatrix3fv(location, count, b(transpose), it.addressOf(offset)) }
    }
    fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) {
        value.usePinned { platform.gles3.glUniformMatrix4fv(location, count, b(transpose), it.addressOf(offset)) }
    }
    fun glUseProgram(program: Int) = platform.gles3.glUseProgram(program.convert())

    fun glVertexAttrib1f(index: Int, x: Float) { if (index >= 0) platform.gles3.glVertexAttrib1f(index.convert(), x) }
    fun glVertexAttrib2f(index: Int, x: Float, y: Float) { if (index >= 0) platform.gles3.glVertexAttrib2f(index.convert(), x, y) }
    fun glVertexAttrib3f(index: Int, x: Float, y: Float, z: Float) { if (index >= 0) platform.gles3.glVertexAttrib3f(index.convert(), x, y, z) }
    fun glVertexAttrib4f(index: Int, x: Float, y: Float, z: Float, w: Float) { if (index >= 0) platform.gles3.glVertexAttrib4f(index.convert(), x, y, z, w) }

    /** Client-side vertex array: the buffer must be direct (allocateDirect) so its memory stays put until the draw. */
    fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: Buffer) {
        if (indx < 0) return
        val p = ptr.nativeAddress() ?: return
        platform.gles3.glVertexAttribPointer(indx.convert(), size, type.convert(), b(normalized), stride, p)
    }
    fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) {
        if (indx < 0) return
        platform.gles3.glVertexAttribPointer(indx.convert(), size, type.convert(), b(normalized), stride, offset.toLong().toCPointer<ByteVar>())
    }
    fun glViewport(x: Int, y: Int, width: Int, height: Int) = platform.gles3.glViewport(x, y, width, height)
}

object GLES30 {
    const val GL_SRGB8_ALPHA8 = 0x8C43
    const val GL_SRGB8 = 0x8C41
    const val GL_RGBA8 = 0x8058
    const val GL_RED = 0x1903
    const val GL_R8 = 0x8229
    const val GL_HALF_FLOAT = 0x140B
    const val GL_DEPTH_COMPONENT24 = 0x81A6
    const val GL_TEXTURE_MAX_LEVEL = 0x813D
    const val GL_TEXTURE_3D = 0x806F
}

@Suppress("unused")
private val keepTypes: List<Any> = listOf(IntVar::class, FloatVar::class, UIntVar::class, IntVar::class)
