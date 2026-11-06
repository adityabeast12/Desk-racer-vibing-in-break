@file:Suppress("unused")

package android.opengl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Port of android.opengl.Matrix (column-major 4x4, same results as Android). */
object Matrix {
    private val tmp = FloatArray(32)

    fun multiplyMM(result: FloatArray, resultOffset: Int, lhs: FloatArray, lhsOffset: Int, rhs: FloatArray, rhsOffset: Int) {
        val t = FloatArray(16)
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += lhs[lhsOffset + i + 4 * k] * rhs[rhsOffset + k + 4 * j]
                t[i + 4 * j] = s
            }
        }
        t.copyInto(result, resultOffset)
    }

    fun multiplyMV(resultVec: FloatArray, resultVecOffset: Int, lhsMat: FloatArray, lhsMatOffset: Int, rhsVec: FloatArray, rhsVecOffset: Int) {
        val x = rhsVec[rhsVecOffset]; val y = rhsVec[rhsVecOffset + 1]; val z = rhsVec[rhsVecOffset + 2]; val w = rhsVec[rhsVecOffset + 3]
        for (i in 0 until 4) {
            resultVec[resultVecOffset + i] = lhsMat[lhsMatOffset + i] * x + lhsMat[lhsMatOffset + 4 + i] * y +
                lhsMat[lhsMatOffset + 8 + i] * z + lhsMat[lhsMatOffset + 12 + i] * w
        }
    }

    fun transposeM(mTrans: FloatArray, mTransOffset: Int, m: FloatArray, mOffset: Int) {
        for (i in 0 until 4) {
            val mBase = i * 4 + mOffset
            mTrans[i + mTransOffset] = m[mBase]
            mTrans[i + 4 + mTransOffset] = m[mBase + 1]
            mTrans[i + 8 + mTransOffset] = m[mBase + 2]
            mTrans[i + 12 + mTransOffset] = m[mBase + 3]
        }
    }

    fun invertM(mInv: FloatArray, mInvOffset: Int, m: FloatArray, mOffset: Int): Boolean {
        val src0 = m[mOffset + 0]; val src4 = m[mOffset + 1]; val src8 = m[mOffset + 2]; val src12 = m[mOffset + 3]
        val src1 = m[mOffset + 4]; val src5 = m[mOffset + 5]; val src9 = m[mOffset + 6]; val src13 = m[mOffset + 7]
        val src2 = m[mOffset + 8]; val src6 = m[mOffset + 9]; val src10 = m[mOffset + 10]; val src14 = m[mOffset + 11]
        val src3 = m[mOffset + 12]; val src7 = m[mOffset + 13]; val src11 = m[mOffset + 14]; val src15 = m[mOffset + 15]
        val atmp0 = src10 * src15; val atmp1 = src11 * src14; val atmp2 = src9 * src15; val atmp3 = src11 * src13
        val atmp4 = src9 * src14; val atmp5 = src10 * src13; val atmp6 = src8 * src15; val atmp7 = src11 * src12
        val atmp8 = src8 * src14; val atmp9 = src10 * src12; val atmp10 = src8 * src13; val atmp11 = src9 * src12
        val dst0 = (atmp0 * src5 + atmp3 * src6 + atmp4 * src7) - (atmp1 * src5 + atmp2 * src6 + atmp5 * src7)
        val dst1 = (atmp1 * src4 + atmp6 * src6 + atmp9 * src7) - (atmp0 * src4 + atmp7 * src6 + atmp8 * src7)
        val dst2 = (atmp2 * src4 + atmp7 * src5 + atmp10 * src7) - (atmp3 * src4 + atmp6 * src5 + atmp11 * src7)
        val dst3 = (atmp5 * src4 + atmp8 * src5 + atmp11 * src6) - (atmp4 * src4 + atmp9 * src5 + atmp10 * src6)
        val dst4 = (atmp1 * src1 + atmp2 * src2 + atmp5 * src3) - (atmp0 * src1 + atmp3 * src2 + atmp4 * src3)
        val dst5 = (atmp0 * src0 + atmp7 * src2 + atmp8 * src3) - (atmp1 * src0 + atmp6 * src2 + atmp9 * src3)
        val dst6 = (atmp3 * src0 + atmp6 * src1 + atmp11 * src3) - (atmp2 * src0 + atmp7 * src1 + atmp10 * src3)
        val dst7 = (atmp4 * src0 + atmp9 * src1 + atmp10 * src2) - (atmp5 * src0 + atmp8 * src1 + atmp11 * src2)
        val btmp0 = src2 * src7; val btmp1 = src3 * src6; val btmp2 = src1 * src7; val btmp3 = src3 * src5
        val btmp4 = src1 * src6; val btmp5 = src2 * src5; val btmp6 = src0 * src7; val btmp7 = src3 * src4
        val btmp8 = src0 * src6; val btmp9 = src2 * src4; val btmp10 = src0 * src5; val btmp11 = src1 * src4
        val dst8 = (btmp0 * src13 + btmp3 * src14 + btmp4 * src15) - (btmp1 * src13 + btmp2 * src14 + btmp5 * src15)
        val dst9 = (btmp1 * src12 + btmp6 * src14 + btmp9 * src15) - (btmp0 * src12 + btmp7 * src14 + btmp8 * src15)
        val dst10 = (btmp2 * src12 + btmp7 * src13 + btmp10 * src15) - (btmp3 * src12 + btmp6 * src13 + btmp11 * src15)
        val dst11 = (btmp5 * src12 + btmp8 * src13 + btmp11 * src14) - (btmp4 * src12 + btmp9 * src13 + btmp10 * src14)
        val dst12 = (btmp2 * src10 + btmp5 * src11 + btmp1 * src9) - (btmp4 * src11 + btmp0 * src9 + btmp3 * src10)
        val dst13 = (btmp8 * src11 + btmp0 * src8 + btmp7 * src10) - (btmp6 * src10 + btmp9 * src11 + btmp1 * src8)
        val dst14 = (btmp6 * src9 + btmp11 * src11 + btmp3 * src8) - (btmp10 * src11 + btmp2 * src8 + btmp7 * src9)
        val dst15 = (btmp10 * src10 + btmp4 * src8 + btmp9 * src9) - (btmp8 * src9 + btmp11 * src10 + btmp5 * src8)
        val det = src0 * dst0 + src1 * dst1 + src2 * dst2 + src3 * dst3
        if (det == 0.0f) return false
        val invdet = 1.0f / det
        val d = floatArrayOf(dst0, dst1, dst2, dst3, dst4, dst5, dst6, dst7, dst8, dst9, dst10, dst11, dst12, dst13, dst14, dst15)
        for (i in 0 until 16) mInv[mInvOffset + i] = d[i] * invdet
        return true
    }

    fun orthoM(m: FloatArray, mOffset: Int, left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float) {
        val rw = 1.0f / (right - left); val rh = 1.0f / (top - bottom); val rd = 1.0f / (far - near)
        val x = 2.0f * rw; val y = 2.0f * rh; val z = -2.0f * rd
        val tx = -(right + left) * rw; val ty = -(top + bottom) * rh; val tz = -(far + near) * rd
        for (i in 0 until 16) m[mOffset + i] = 0f
        m[mOffset + 0] = x; m[mOffset + 5] = y; m[mOffset + 10] = z
        m[mOffset + 12] = tx; m[mOffset + 13] = ty; m[mOffset + 14] = tz; m[mOffset + 15] = 1.0f
    }

    fun frustumM(m: FloatArray, offset: Int, left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float) {
        val rw = 1.0f / (right - left); val rh = 1.0f / (top - bottom); val rd = 1.0f / (near - far)
        val x = 2.0f * (near * rw); val y = 2.0f * (near * rh)
        val a = (right + left) * rw; val b = (top + bottom) * rh
        val c = (far + near) * rd; val d = 2.0f * (far * near * rd)
        for (i in 0 until 16) m[offset + i] = 0f
        m[offset + 0] = x; m[offset + 5] = y; m[offset + 8] = a; m[offset + 9] = b
        m[offset + 10] = c; m[offset + 14] = d; m[offset + 11] = -1.0f
    }

    fun perspectiveM(m: FloatArray, offset: Int, fovy: Float, aspect: Float, zNear: Float, zFar: Float) {
        val f = 1.0f / tan(fovy * (PI / 360.0)).toFloat()
        val rangeReciprocal = 1.0f / (zNear - zFar)
        m[offset + 0] = f / aspect; m[offset + 1] = 0f; m[offset + 2] = 0f; m[offset + 3] = 0f
        m[offset + 4] = 0f; m[offset + 5] = f; m[offset + 6] = 0f; m[offset + 7] = 0f
        m[offset + 8] = 0f; m[offset + 9] = 0f
        m[offset + 10] = (zFar + zNear) * rangeReciprocal; m[offset + 11] = -1.0f
        m[offset + 12] = 0f; m[offset + 13] = 0f
        m[offset + 14] = 2.0f * zFar * zNear * rangeReciprocal; m[offset + 15] = 0f
    }

    fun length(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

    fun setIdentityM(sm: FloatArray, smOffset: Int) {
        for (i in 0 until 16) sm[smOffset + i] = 0f
        var i = 0
        while (i < 16) { sm[smOffset + i] = 1.0f; i += 5 }
    }

    fun scaleM(sm: FloatArray, smOffset: Int, m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            sm[smOffset + i] = m[mOffset + i] * x
            sm[smOffset + 4 + i] = m[mOffset + 4 + i] * y
            sm[smOffset + 8 + i] = m[mOffset + 8 + i] * z
            sm[smOffset + 12 + i] = m[mOffset + 12 + i]
        }
    }

    fun scaleM(m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            m[mOffset + i] *= x
            m[mOffset + 4 + i] *= y
            m[mOffset + 8 + i] *= z
        }
    }

    fun translateM(tm: FloatArray, tmOffset: Int, m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 12) tm[tmOffset + i] = m[mOffset + i]
        for (i in 0 until 4) {
            tm[tmOffset + 12 + i] = m[mOffset + i] * x + m[mOffset + 4 + i] * y + m[mOffset + 8 + i] * z + m[mOffset + 12 + i]
        }
    }

    fun translateM(m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            m[mOffset + 12 + i] += m[mOffset + i] * x + m[mOffset + 4 + i] * y + m[mOffset + 8 + i] * z
        }
    }

    fun rotateM(rm: FloatArray, rmOffset: Int, m: FloatArray, mOffset: Int, a: Float, x: Float, y: Float, z: Float) {
        val r = FloatArray(16)
        setRotateM(r, 0, a, x, y, z)
        multiplyMM(rm, rmOffset, m, mOffset, r, 0)
    }

    fun rotateM(m: FloatArray, mOffset: Int, a: Float, x: Float, y: Float, z: Float) {
        val r = FloatArray(16)
        setRotateM(r, 0, a, x, y, z)
        val out = FloatArray(16)
        multiplyMM(out, 0, m, mOffset, r, 0)
        out.copyInto(m, mOffset)
    }

    fun setRotateM(rm: FloatArray, rmOffset: Int, aDeg: Float, xIn: Float, yIn: Float, zIn: Float) {
        var x = xIn; var y = yIn; var z = zIn
        rm[rmOffset + 3] = 0f; rm[rmOffset + 7] = 0f; rm[rmOffset + 11] = 0f
        rm[rmOffset + 12] = 0f; rm[rmOffset + 13] = 0f; rm[rmOffset + 14] = 0f
        rm[rmOffset + 15] = 1f
        val a = aDeg * (PI / 180.0).toFloat()
        val s = sin(a)
        val c = cos(a)
        if (1.0f == x && 0.0f == y && 0.0f == z) {
            rm[rmOffset + 5] = c; rm[rmOffset + 10] = c
            rm[rmOffset + 6] = s; rm[rmOffset + 9] = -s
            rm[rmOffset + 1] = 0f; rm[rmOffset + 2] = 0f
            rm[rmOffset + 4] = 0f; rm[rmOffset + 8] = 0f
            rm[rmOffset + 0] = 1f
        } else if (0.0f == x && 1.0f == y && 0.0f == z) {
            rm[rmOffset + 0] = c; rm[rmOffset + 10] = c
            rm[rmOffset + 8] = s; rm[rmOffset + 2] = -s
            rm[rmOffset + 1] = 0f; rm[rmOffset + 4] = 0f
            rm[rmOffset + 6] = 0f; rm[rmOffset + 9] = 0f
            rm[rmOffset + 5] = 1f
        } else if (0.0f == x && 0.0f == y && 1.0f == z) {
            rm[rmOffset + 0] = c; rm[rmOffset + 5] = c
            rm[rmOffset + 1] = s; rm[rmOffset + 4] = -s
            rm[rmOffset + 2] = 0f; rm[rmOffset + 6] = 0f
            rm[rmOffset + 8] = 0f; rm[rmOffset + 9] = 0f
            rm[rmOffset + 10] = 1f
        } else {
            val len = length(x, y, z)
            if (1.0f != len) {
                val recipLen = 1.0f / len
                x *= recipLen; y *= recipLen; z *= recipLen
            }
            val nc = 1.0f - c
            val xy = x * y; val yz = y * z; val zx = z * x
            val xs = x * s; val ys = y * s; val zs = z * s
            rm[rmOffset + 0] = x * x * nc + c
            rm[rmOffset + 4] = xy * nc - zs
            rm[rmOffset + 8] = zx * nc + ys
            rm[rmOffset + 1] = xy * nc + zs
            rm[rmOffset + 5] = y * y * nc + c
            rm[rmOffset + 9] = yz * nc - xs
            rm[rmOffset + 2] = zx * nc - ys
            rm[rmOffset + 6] = yz * nc + xs
            rm[rmOffset + 10] = z * z * nc + c
        }
    }

    fun setLookAtM(rm: FloatArray, rmOffset: Int, eyeX: Float, eyeY: Float, eyeZ: Float, centerX: Float, centerY: Float, centerZ: Float, upX: Float, upY: Float, upZ: Float) {
        var fx = centerX - eyeX
        var fy = centerY - eyeY
        var fz = centerZ - eyeZ
        val rlf = 1.0f / length(fx, fy, fz)
        fx *= rlf; fy *= rlf; fz *= rlf
        var sx = fy * upZ - fz * upY
        var sy = fz * upX - fx * upZ
        var sz = fx * upY - fy * upX
        val rls = 1.0f / length(sx, sy, sz)
        sx *= rls; sy *= rls; sz *= rls
        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx
        rm[rmOffset + 0] = sx; rm[rmOffset + 1] = ux; rm[rmOffset + 2] = -fx; rm[rmOffset + 3] = 0.0f
        rm[rmOffset + 4] = sy; rm[rmOffset + 5] = uy; rm[rmOffset + 6] = -fy; rm[rmOffset + 7] = 0.0f
        rm[rmOffset + 8] = sz; rm[rmOffset + 9] = uz; rm[rmOffset + 10] = -fz; rm[rmOffset + 11] = 0.0f
        rm[rmOffset + 12] = 0.0f; rm[rmOffset + 13] = 0.0f; rm[rmOffset + 14] = 0.0f; rm[rmOffset + 15] = 1.0f
        translateM(rm, rmOffset, -eyeX, -eyeY, -eyeZ)
    }
}
