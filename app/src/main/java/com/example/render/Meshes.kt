package com.example.render

import com.example.game.Track
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GlMesh(val buffer: FloatBuffer, val vertexCount: Int)

object Meshes {

    /**
     * Interleaved 9 floats per vertex: (px, py, pz, nx, ny, nz, r, g, b)
     */
    const val LIT_STRIDE_BYTES = 9 * 4

    fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    fun buildUnitCube(): GlMesh {
        val out = ArrayList<Float>(36 * 9)
        fun addFace(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            nx: Float, ny: Float, nz: Float
        ) {
            val verts = arrayOf(
                floatArrayOf(x0, y0, z0), floatArrayOf(x1, y1, z1), floatArrayOf(x2, y2, z2),
                floatArrayOf(x0, y0, z0), floatArrayOf(x2, y2, z2), floatArrayOf(x3, y3, z3)
            )
            for (v in verts) {
                out.add(v[0]); out.add(v[1]); out.add(v[2])
                out.add(nx); out.add(ny); out.add(nz)
                out.add(1f); out.add(1f); out.add(1f)
            }
        }
        addFace(-0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0f, 1f, 0f)
        addFace(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0f, -1f, 0f)
        addFace(-0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0f, 0f, 1f)
        addFace(0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0f, 0f, -1f)
        addFace(0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 1f, 0f, 0f)
        addFace(-0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -1f, 0f, 0f)
        val arr = out.toFloatArray()
        return GlMesh(createFloatBuffer(arr), arr.size / 9)
    }

    fun buildRainbowItemCube(): GlMesh {
        val out = ArrayList<Float>(36 * 9)
        fun addFace(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            nx: Float, ny: Float, nz: Float,
            r: Float, g: Float, b: Float
        ) {
            val verts = arrayOf(
                floatArrayOf(x0, y0, z0), floatArrayOf(x1, y1, z1), floatArrayOf(x2, y2, z2),
                floatArrayOf(x0, y0, z0), floatArrayOf(x2, y2, z2), floatArrayOf(x3, y3, z3)
            )
            for (v in verts) {
                out.add(v[0]); out.add(v[1]); out.add(v[2])
                out.add(nx); out.add(ny); out.add(nz)
                out.add(r); out.add(g); out.add(b)
            }
        }
        addFace(-0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 0.22f, 0.74f, 0.98f)
        addFace(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0f, -1f, 0f, 0.66f, 0.33f, 0.97f)
        addFace(-0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0f, 0f, 1f, 0.98f, 0.80f, 0.08f)
        addFace(0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 0.13f, 0.77f, 0.37f)
        addFace(0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 1.0f, 0.16f, 0.37f)
        addFace(-0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -1f, 0f, 0f, 0.0f, 0.96f, 0.83f)
        val arr = out.toFloatArray()
        return GlMesh(createFloatBuffer(arr), arr.size / 9)
    }

    fun buildSphere(stacks: Int = 10, slices: Int = 14): GlMesh {
        val out = ArrayList<Float>()
        for (i in 0 until stacks) {
            val phi0 = PI * (i.toDouble() / stacks - 0.5)
            val phi1 = PI * ((i + 1).toDouble() / stacks - 0.5)
            val y0 = sin(phi0).toFloat(); val r0 = cos(phi0).toFloat()
            val y1 = sin(phi1).toFloat(); val r1 = cos(phi1).toFloat()

            for (j in 0 until slices) {
                val th0 = 2.0 * PI * j / slices
                val th1 = 2.0 * PI * (j + 1) / slices
                val x00 = r0 * cos(th0).toFloat(); val z00 = r0 * sin(th0).toFloat()
                val x01 = r0 * cos(th1).toFloat(); val z01 = r0 * sin(th1).toFloat()
                val x10 = r1 * cos(th0).toFloat(); val z10 = r1 * sin(th0).toFloat()
                val x11 = r1 * cos(th1).toFloat(); val z11 = r1 * sin(th1).toFloat()

                val tris = arrayOf(
                    floatArrayOf(x00, y0, z00), floatArrayOf(x10, y1, z10), floatArrayOf(x11, y1, z11),
                    floatArrayOf(x00, y0, z00), floatArrayOf(x11, y1, z11), floatArrayOf(x01, y0, z01)
                )
                for (v in tris) {
                    out.add(v[0]); out.add(v[1]); out.add(v[2])
                    out.add(v[0]); out.add(v[1]); out.add(v[2])
                    out.add(1f); out.add(1f); out.add(1f)
                }
            }
        }
        val arr = out.toFloatArray()
        return GlMesh(createFloatBuffer(arr), arr.size / 9)
    }

    fun buildWheelCylinder(slices: Int = 14): GlMesh {
        val out = ArrayList<Float>()
        for (i in 0 until slices) {
            val a0 = (2.0 * PI * i / slices).toFloat()
            val a1 = (2.0 * PI * (i + 1) / slices).toFloat()
            val y0 = cos(a0); val z0 = sin(a0)
            val y1 = cos(a1); val z1 = sin(a1)

            val quad = arrayOf(
                floatArrayOf(-0.5f, y0, z0, 0f, y0, z0),
                floatArrayOf(0.5f, y0, z0, 0f, y0, z0),
                floatArrayOf(0.5f, y1, z1, 0f, y1, z1),
                floatArrayOf(-0.5f, y0, z0, 0f, y0, z0),
                floatArrayOf(0.5f, y1, z1, 0f, y1, z1),
                floatArrayOf(-0.5f, y1, z1, 0f, y1, z1)
            )
            for (v in quad) {
                for (c in v) out.add(c)
                out.add(1f); out.add(1f); out.add(1f)
            }
            val caps = arrayOf(
                floatArrayOf(0.5f, 0f, 0f, 1f, 0f, 0f),
                floatArrayOf(0.5f, y0, z0, 1f, 0f, 0f),
                floatArrayOf(0.5f, y1, z1, 1f, 0f, 0f),
                floatArrayOf(-0.5f, 0f, 0f, -1f, 0f, 0f),
                floatArrayOf(-0.5f, y1, z1, -1f, 0f, 0f),
                floatArrayOf(-0.5f, y0, z0, -1f, 0f, 0f)
            )
            for (v in caps) {
                for (c in v) out.add(c)
                out.add(0.85f); out.add(0.85f); out.add(0.85f)
            }
        }
        val arr = out.toFloatArray()
        return GlMesh(createFloatBuffer(arr), arr.size / 9)
    }

    /**
     * Generates the complete 3D procedural Track Ribbon Mesh (banked road strip, dashed centre line,
     * alternating kerbs, elevated bridge guardrails + support pillars, and checkered start/finish line)
     * from the closed Catmull-Rom spline.
     */
    fun buildTrackRoadMesh(track: Track): GlMesh {
        val out = ArrayList<Float>(track.samples.size * 54 * 9)
        val roadRgb = hexToRgb(track.roadSurfaceHex)
        val kerb1Rgb = hexToRgb(track.kerbPrimaryHex)
        val kerb2Rgb = hexToRgb(track.kerbSecondaryHex)

        val halfWidth = track.roadWidth * 0.5f
        val kerbWidth = 0.90f
        val n = track.samples.size

        fun putQuadWithNormal(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
            cx: Float, cy: Float, cz: Float,
            dx: Float, dy: Float, dz: Float,
            nx: Float, ny: Float, nz: Float,
            r: Float, g: Float, b: Float
        ) {
            val verts = arrayOf(
                floatArrayOf(ax, ay, az), floatArrayOf(bx, by, bz), floatArrayOf(cx, cy, cz),
                floatArrayOf(ax, ay, az), floatArrayOf(cx, cy, cz), floatArrayOf(dx, dy, dz)
            )
            for (v in verts) {
                out.add(v[0]); out.add(v[1]); out.add(v[2])
                out.add(nx); out.add(ny); out.add(nz)
                out.add(r); out.add(g); out.add(b)
            }
        }

        fun putQuad(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
            cx: Float, cy: Float, cz: Float,
            dx: Float, dy: Float, dz: Float,
            r: Float, g: Float, b: Float
        ) {
            putQuadWithNormal(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, 0f, 1f, 0f, r, g, b)
        }

        for (i in 0 until n) {
            val s0 = track.samples[i]
            val s1 = track.samples[(i + 1) % n]

            val bankDy0 = sin(s0.bankAngleRad) * halfWidth * 0.35f
            val bankDy1 = sin(s1.bankAngleRad) * halfWidth * 0.35f

            val y0L = s0.pos.y + 0.03f - bankDy0
            val y0R = s0.pos.y + 0.03f + bankDy0
            val y1L = s1.pos.y + 0.03f - bankDy1
            val y1R = s1.pos.y + 0.03f + bankDy1

            val l0x = s0.pos.x - s0.rightX * halfWidth
            val l0z = s0.pos.z - s0.rightZ * halfWidth
            val r0x = s0.pos.x + s0.rightX * halfWidth
            val r0z = s0.pos.z + s0.rightZ * halfWidth

            val l1x = s1.pos.x - s1.rightX * halfWidth
            val l1z = s1.pos.z - s1.rightZ * halfWidth
            val r1x = s1.pos.x + s1.rightX * halfWidth
            val r1z = s1.pos.z + s1.rightZ * halfWidth

            // 1. Main Road Ribbon (subtle alternating shade strips for speed sensation)
            val shade = if ((i / 2) % 2 == 0) 1.0f else 0.96f
            putQuad(
                l0x, y0L, l0z,
                r0x, y0R, r0z,
                r1x, y1R, r1z,
                l1x, y1L, l1z,
                roadRgb[0] * shade, roadRgb[1] * shade, roadRgb[2] * shade
            )

            // 2. Left & Right Alternating Kerb Strips
            val kerbRgb = if ((i / 2) % 2 == 0) kerb1Rgb else kerb2Rgb
            val kl0x = l0x - s0.rightX * kerbWidth
            val kl0z = l0z - s0.rightZ * kerbWidth
            val kl1x = l1x - s1.rightX * kerbWidth
            val kl1z = l1z - s1.rightZ * kerbWidth
            putQuad(
                kl0x, y0L + 0.02f, kl0z,
                l0x, y0L + 0.02f, l0z,
                l1x, y1L + 0.02f, l1z,
                kl1x, y1L + 0.02f, kl1z,
                kerbRgb[0], kerbRgb[1], kerbRgb[2]
            )

            val kr0x = r0x + s0.rightX * kerbWidth
            val kr0z = r0z + s0.rightZ * kerbWidth
            val kr1x = r1x + s1.rightX * kerbWidth
            val kr1z = r1z + s1.rightZ * kerbWidth
            putQuad(
                r0x, y0R + 0.02f, r0z,
                kr0x, y0R + 0.02f, kr0z,
                kr1x, y1R + 0.02f, kr1z,
                r1x, y1R + 0.02f, r1z,
                kerbRgb[0], kerbRgb[1], kerbRgb[2]
            )

            // 3. Elevated Bridge Side Walls & Support Pillars when road is elevated (y > 0.5)
            if (s0.pos.y > 0.5f || s1.pos.y > 0.5f) {
                val wallH = 0.85f
                putQuadWithNormal(
                    kl0x, y0L, kl0z,
                    kl1x, y1L, kl1z,
                    kl1x, y1L + wallH, kl1z,
                    kl0x, y0L + wallH, kl0z,
                    s0.rightX, 0.2f, s0.rightZ,
                    kerbRgb[0] * 0.85f, kerbRgb[1] * 0.85f, kerbRgb[2] * 0.85f
                )
                putQuadWithNormal(
                    kr1x, y1R, kr1z,
                    kr0x, y0R, kr0z,
                    kr0x, y0R + wallH, kr0z,
                    kr1x, y1R + wallH, kr1z,
                    -s0.rightX, 0.2f, -s0.rightZ,
                    kerbRgb[0] * 0.85f, kerbRgb[1] * 0.85f, kerbRgb[2] * 0.85f
                )
            }

            // 4. Dashed Centre Line
            if ((i / 3) % 2 == 0) {
                val cw = 0.25f
                val cy0 = (y0L + y0R) * 0.5f + 0.025f
                val cy1 = (y1L + y1R) * 0.5f + 0.025f
                putQuad(
                    s0.pos.x - s0.rightX * cw, cy0, s0.pos.z - s0.rightZ * cw,
                    s0.pos.x + s0.rightX * cw, cy0, s0.pos.z + s0.rightZ * cw,
                    s1.pos.x + s1.rightX * cw, cy1, s1.pos.z + s1.rightZ * cw,
                    s1.pos.x - s1.rightX * cw, cy1, s1.pos.z - s1.rightZ * cw,
                    0.96f, 0.82f, 0.18f
                )
            }
        }

        // 5. Procedural Checkered Start/Finish Line at sample 0
        val sStart = track.samples[0]
        val sNext = track.samples[2]
        val tilesAcross = 10
        for (col in 0 until tilesAcross) {
            val f0 = (col.toFloat() / tilesAcross) * 2f - 1f
            val f1 = ((col + 1).toFloat() / tilesAcross) * 2f - 1f
            val lat0 = f0 * halfWidth
            val lat1 = f1 * halfWidth
            val isWhite = col % 2 == 0
            val cA = if (isWhite) 0.98f else 0.08f
            putQuad(
                sStart.pos.x + sStart.rightX * lat0, sStart.pos.y + 0.06f, sStart.pos.z + sStart.rightZ * lat0,
                sStart.pos.x + sStart.rightX * lat1, sStart.pos.y + 0.06f, sStart.pos.z + sStart.rightZ * lat1,
                sNext.pos.x + sNext.rightX * lat1, sNext.pos.y + 0.06f, sNext.pos.z + sNext.rightZ * lat1,
                sNext.pos.x + sNext.rightX * lat0, sNext.pos.y + 0.06f, sNext.pos.z + sNext.rightZ * lat0,
                cA, cA, cA
            )
        }

        val arr = out.toFloatArray()
        return GlMesh(createFloatBuffer(arr), arr.size / 9)
    }

    fun hexToRgb(hex: Long): FloatArray {
        val r = ((hex shr 16) and 0xFF) / 255f
        val g = ((hex shr 8) and 0xFF) / 255f
        val b = (hex and 0xFF) / 255f
        return floatArrayOf(r, g, b)
    }
}
