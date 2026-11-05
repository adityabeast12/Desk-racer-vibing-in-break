package com.example.render

import com.example.game.Track
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GlMesh(
    val buffer: FloatBuffer,
    val vertexCount: Int,
    val tangentBuffer: FloatBuffer? = null
)

class TrackRenderMeshes(
    val deskSlabMesh: GlMesh,
    val roadRibbonMesh: GlMesh,
    val kerbRibbonMesh: GlMesh,
    val finishBannerMesh: GlMesh,
    val bridgeSupportMesh: GlMesh
)

object Meshes {

    /**
     * Interleaved 11 floats per vertex: (px, py, pz, nx, ny, nz, u, v, r, g, b)
     */
    const val LIT_STRIDE_BYTES = 11 * 4

    /**
     * Interleaved 5 floats per vertex for backdrop sphere: (px, py, pz, u, v)
     */
    const val BACKDROP_STRIDE_BYTES = 5 * 4

    /**
     * Interleaved 7 floats per vertex for unlit contact AO / particle meshes: (px, py, pz, r, g, b, a)
     */
    const val UNLIT_STRIDE_BYTES = 7 * 4

    fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    /**
     * Flat unit XZ quad [-0.5..0.5] at y = 0 facing +Y with top-left UV origin (v=0 at -Z, v=1 at +Z).
     * Used for desk decals, start grid boxes, boost pad chevrons, and the garage turntable disc.
     */
    fun buildFlatQuadXZ(): GlMesh {
        val verts = floatArrayOf(
            // px, py, pz,   nx, ny, nz,   u, v,   r, g, b
            -0.5f, 0f, -0.5f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 1f,
            -0.5f, 0f,  0.5f, 0f, 1f, 0f, 0f, 1f, 1f, 1f, 1f,
             0.5f, 0f,  0.5f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
            -0.5f, 0f, -0.5f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 1f,
             0.5f, 0f,  0.5f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
             0.5f, 0f, -0.5f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f
        )
        return GlMesh(createFloatBuffer(verts), 6)
    }

    fun buildUnitCube(): GlMesh {
        val out = ArrayList<Float>(36 * 11)
        fun addFace(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            nx: Float, ny: Float, nz: Float
        ) {
            val verts = arrayOf(
                floatArrayOf(x0, y0, z0, 0f, 0f),
                floatArrayOf(x1, y1, z1, 0f, 1f),
                floatArrayOf(x2, y2, z2, 1f, 1f),
                floatArrayOf(x0, y0, z0, 0f, 0f),
                floatArrayOf(x2, y2, z2, 1f, 1f),
                floatArrayOf(x3, y3, z3, 1f, 0f)
            )
            for (v in verts) {
                out.add(v[0]); out.add(v[1]); out.add(v[2])
                out.add(nx); out.add(ny); out.add(nz)
                out.add(v[3]); out.add(v[4])
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
        return GlMesh(createFloatBuffer(arr), arr.size / 11)
    }

    fun buildRainbowItemCube(): GlMesh {
        val out = ArrayList<Float>(36 * 11)
        fun addFace(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            nx: Float, ny: Float, nz: Float,
            r: Float, g: Float, b: Float
        ) {
            val verts = arrayOf(
                floatArrayOf(x0, y0, z0, 0f, 0f),
                floatArrayOf(x1, y1, z1, 0f, 1f),
                floatArrayOf(x2, y2, z2, 1f, 1f),
                floatArrayOf(x0, y0, z0, 0f, 0f),
                floatArrayOf(x2, y2, z2, 1f, 1f),
                floatArrayOf(x3, y3, z3, 1f, 0f)
            )
            for (v in verts) {
                out.add(v[0]); out.add(v[1]); out.add(v[2])
                out.add(nx); out.add(ny); out.add(nz)
                out.add(v[3]); out.add(v[4])
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
        return GlMesh(createFloatBuffer(arr), arr.size / 11)
    }

    fun buildSphere(stacks: Int = 10, slices: Int = 14): GlMesh {
        val out = ArrayList<Float>()
        for (i in 0 until stacks) {
            val v0 = i.toFloat() / stacks
            val v1 = (i + 1).toFloat() / stacks
            // Top-left UV origin: v=0 at top (+Y), v=1 at bottom (-Y)
            val phi0 = PI * (0.5 - v0)
            val phi1 = PI * (0.5 - v1)
            val y0 = sin(phi0).toFloat(); val r0 = cos(phi0).toFloat()
            val y1 = sin(phi1).toFloat(); val r1 = cos(phi1).toFloat()

            for (j in 0 until slices) {
                val u0 = j.toFloat() / slices
                val u1 = (j + 1).toFloat() / slices
                val th0 = 2.0 * PI * u0
                val th1 = 2.0 * PI * u1
                val x00 = r0 * cos(th0).toFloat(); val z00 = r0 * sin(th0).toFloat()
                val x01 = r0 * cos(th1).toFloat(); val z01 = r0 * sin(th1).toFloat()
                val x10 = r1 * cos(th0).toFloat(); val z10 = r1 * sin(th0).toFloat()
                val x11 = r1 * cos(th1).toFloat(); val z11 = r1 * sin(th1).toFloat()

                val tris = arrayOf(
                    floatArrayOf(x00, y0, z00, u0, v0),
                    floatArrayOf(x10, y1, z10, u0, v1),
                    floatArrayOf(x11, y1, z11, u1, v1),
                    floatArrayOf(x00, y0, z00, u0, v0),
                    floatArrayOf(x11, y1, z11, u1, v1),
                    floatArrayOf(x01, y0, z01, u1, v0)
                )
                for (v in tris) {
                    out.add(v[0]); out.add(v[1]); out.add(v[2])
                    out.add(v[0]); out.add(v[1]); out.add(v[2])
                    out.add(v[3]); out.add(v[4])
                    out.add(1f); out.add(1f); out.add(1f)
                }
            }
        }
        val arr = out.toFloatArray()
        return GlMesh(createFloatBuffer(arr), arr.size / 11)
    }

    /**
     * Inside-out Equirectangular Backdrop Sphere (radius = 900 u, 5 floats/vert: px,py,pz, u,v).
     * Top-left UV origin: v=0 at top pole (+Y), v=1 at bottom pole (-Y).
     */
    fun buildBackdropSphere(radius: Float = 900f, stacks: Int = 18, slices: Int = 28): GlMesh {
        val out = ArrayList<Float>(stacks * slices * 6 * 5)
        for (i in 0 until stacks) {
            val v0 = i.toFloat() / stacks
            val v1 = (i + 1).toFloat() / stacks
            val lat0 = PI * (0.5 - v0)
            val lat1 = PI * (0.5 - v1)
            val y0 = (sin(lat0) * radius).toFloat()
            val r0 = (cos(lat0) * radius).toFloat()
            val y1 = (sin(lat1) * radius).toFloat()
            val r1 = (cos(lat1) * radius).toFloat()

            for (j in 0 until slices) {
                val u0 = j.toFloat() / slices
                val u1 = (j + 1).toFloat() / slices
                val lon0 = 2.0 * PI * (u0 - 0.5)
                val lon1 = 2.0 * PI * (u1 - 0.5)

                val x00 = r0 * cos(lon0).toFloat(); val z00 = r0 * sin(lon0).toFloat()
                val x01 = r0 * cos(lon1).toFloat(); val z01 = r0 * sin(lon1).toFloat()
                val x10 = r1 * cos(lon0).toFloat(); val z10 = r1 * sin(lon0).toFloat()
                val x11 = r1 * cos(lon1).toFloat(); val z11 = r1 * sin(lon1).toFloat()

                // Inside-facing winding
                val tris = arrayOf(
                    floatArrayOf(x00, y0, z00, u0, v0),
                    floatArrayOf(x11, y1, z11, u1, v1),
                    floatArrayOf(x10, y1, z10, u0, v1),
                    floatArrayOf(x00, y0, z00, u0, v0),
                    floatArrayOf(x01, y0, z01, u1, v0),
                    floatArrayOf(x11, y1, z11, u1, v1)
                )
                for (v in tris) {
                    out.add(v[0]); out.add(v[1]); out.add(v[2])
                    out.add(v[3]); out.add(v[4])
                }
            }
        }
        val arr = out.toFloatArray()
        return GlMesh(createFloatBuffer(arr), arr.size / 5)
    }

    /**
     * Part D9: Soft radial Contact AO / Shadow Disc (unit diameter = 1.0, 7 floats/vert for unlit shader).
     */
    fun buildContactShadowDisc(slices: Int = 18): GlMesh {
        val out = ArrayList<Float>(slices * 6 * 7)
        val innerR = 0.24f
        val outerR = 0.50f
        for (i in 0 until slices) {
            val a0 = (2.0 * PI * i / slices).toFloat()
            val a1 = (2.0 * PI * (i + 1) / slices).toFloat()
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)

            // Inner core triangle (alpha 0.58 -> 0.42)
            val core = arrayOf(
                floatArrayOf(0f, 0f, 0f, 0.02f, 0.03f, 0.05f, 0.58f),
                floatArrayOf(c0 * innerR, 0f, s0 * innerR, 0.02f, 0.03f, 0.05f, 0.42f),
                floatArrayOf(c1 * innerR, 0f, s1 * innerR, 0.02f, 0.03f, 0.05f, 0.42f)
            )
            for (v in core) {
                for (f in v) out.add(f)
            }
            // Outer feather ring (alpha 0.42 -> 0.0)
            val feather = arrayOf(
                floatArrayOf(c0 * innerR, 0f, s0 * innerR, 0.02f, 0.03f, 0.05f, 0.42f),
                floatArrayOf(c0 * outerR, 0f, s0 * outerR, 0.02f, 0.03f, 0.05f, 0.0f),
                floatArrayOf(c1 * outerR, 0f, s1 * outerR, 0.02f, 0.03f, 0.05f, 0.0f),
                floatArrayOf(c0 * innerR, 0f, s0 * innerR, 0.02f, 0.03f, 0.05f, 0.42f),
                floatArrayOf(c1 * outerR, 0f, s1 * outerR, 0.02f, 0.03f, 0.05f, 0.0f),
                floatArrayOf(c1 * innerR, 0f, s1 * innerR, 0.02f, 0.03f, 0.05f, 0.42f)
            )
            for (v in feather) {
                for (f in v) out.add(f)
            }
        }
        val arr = out.toFloatArray()
        return GlMesh(createFloatBuffer(arr), arr.size / 7)
    }

    fun buildWheelCylinder(slices: Int = 14): GlMesh {
        val out = ArrayList<Float>()
        for (i in 0 until slices) {
            val a0 = (2.0 * PI * i / slices).toFloat()
            val a1 = (2.0 * PI * (i + 1) / slices).toFloat()
            val y0 = cos(a0); val z0 = sin(a0)
            val y1 = cos(a1); val z1 = sin(a1)

            val quad = arrayOf(
                floatArrayOf(-0.5f, y0, z0, 0f, y0, z0, 0f, 0f),
                floatArrayOf(0.5f, y0, z0, 0f, y0, z0, 1f, 0f),
                floatArrayOf(0.5f, y1, z1, 0f, y1, z1, 1f, 1f),
                floatArrayOf(-0.5f, y0, z0, 0f, y0, z0, 0f, 0f),
                floatArrayOf(0.5f, y1, z1, 0f, y1, z1, 1f, 1f),
                floatArrayOf(-0.5f, y1, z1, 0f, y1, z1, 0f, 1f)
            )
            for (v in quad) {
                for (c in v) out.add(c)
                out.add(1f); out.add(1f); out.add(1f)
            }
            val caps = arrayOf(
                floatArrayOf(0.5f, 0f, 0f, 1f, 0f, 0f, 0.5f, 0.5f),
                floatArrayOf(0.5f, y0, z0, 1f, 0f, 0f, 0.5f + y0 * 0.5f, 0.5f + z0 * 0.5f),
                floatArrayOf(0.5f, y1, z1, 1f, 0f, 0f, 0.5f + y1 * 0.5f, 0.5f + z1 * 0.5f),
                floatArrayOf(-0.5f, 0f, 0f, -1f, 0f, 0f, 0.5f, 0.5f),
                floatArrayOf(-0.5f, y1, z1, -1f, 0f, 0f, 0.5f + y1 * 0.5f, 0.5f + z1 * 0.5f),
                floatArrayOf(-0.5f, y0, z0, -1f, 0f, 0f, 0.5f + y0 * 0.5f, 0.5f + z0 * 0.5f)
            )
            for (v in caps) {
                for (c in v) out.add(c)
                out.add(0.85f); out.add(0.85f); out.add(0.85f)
            }
        }
        val arr = out.toFloatArray()
        return GlMesh(createFloatBuffer(arr), arr.size / 11)
    }

    /**
     * Part D2: Builds separate UV-mapped meshes for the Desk Tabletop Slab, Road Ribbon (with spline tangent buffer),
     * Red/White Kerb Strips, Checkered Finish Banner, and Elevated Bridge Walls & Pillars.
     */
    fun buildTrackMeshes(track: Track): TrackRenderMeshes {
        val deskOut = ArrayList<Float>(36 * 11)
        val roadOut = ArrayList<Float>(track.samples.size * 6 * 11)
        val roadTanOut = ArrayList<Float>(track.samples.size * 6 * 3)
        val kerbOut = ArrayList<Float>(track.samples.size * 36 * 11)
        val finishOut = ArrayList<Float>(24 * 11)
        val bridgeOut = ArrayList<Float>(track.samples.size * 30 * 11)

        fun putQuadUV(
            target: ArrayList<Float>,
            ax: Float, ay: Float, az: Float, au: Float, av: Float,
            bx: Float, by: Float, bz: Float, bu: Float, bv: Float,
            cx: Float, cy: Float, cz: Float, cu: Float, cv: Float,
            dx: Float, dy: Float, dz: Float, du: Float, dv: Float,
            nx: Float, ny: Float, nz: Float,
            r: Float = 1f, g: Float = 1f, b: Float = 1f
        ) {
            val verts = arrayOf(
                floatArrayOf(ax, ay, az, au, av),
                floatArrayOf(bx, by, bz, bu, bv),
                floatArrayOf(cx, cy, cz, cu, cv),
                floatArrayOf(ax, ay, az, au, av),
                floatArrayOf(cx, cy, cz, cu, cv),
                floatArrayOf(dx, dy, dz, du, dv)
            )
            for (v in verts) {
                target.add(v[0]); target.add(v[1]); target.add(v[2])
                target.add(nx); target.add(ny); target.add(nz)
                target.add(v[3]); target.add(v[4])
                target.add(r); target.add(g); target.add(b)
            }
        }

        // 1. Desk Tabletop Slab (Top tiled surface + 4 thick edge bevel walls)
        val ex = track.deskHalfExtentX
        val ez = track.deskHalfExtentZ
        val tile = track.surfaceTextureTileUnits.coerceAtLeast(10f)
        val maxU = (ex * 2f) / tile
        val maxV = (ez * 2f) / tile
        val topY = -0.02f
        val botY = -5.2f

        // Top surface
        putQuadUV(
            deskOut,
            -ex, topY, -ez, 0f, 0f,
            -ex, topY, ez, 0f, maxV,
            ex, topY, ez, maxU, maxV,
            ex, topY, -ez, maxU, 0f,
            0f, 1f, 0f
        )
        // Front edge (+Z)
        putQuadUV(
            deskOut,
            -ex, botY, ez, 0f, 0f,
            ex, botY, ez, maxU, 0f,
            ex, topY, ez, maxU, 0.25f,
            -ex, topY, ez, 0f, 0.25f,
            0f, 0f, 1f, 0.78f, 0.74f, 0.70f
        )
        // Back edge (-Z)
        putQuadUV(
            deskOut,
            ex, botY, -ez, 0f, 0f,
            -ex, botY, -ez, maxU, 0f,
            -ex, topY, -ez, maxU, 0.25f,
            ex, topY, -ez, 0f, 0.25f,
            0f, 0f, -1f, 0.78f, 0.74f, 0.70f
        )
        // Right edge (+X)
        putQuadUV(
            deskOut,
            ex, botY, ez, 0f, 0f,
            ex, botY, -ez, maxV, 0f,
            ex, topY, -ez, maxV, 0.25f,
            ex, topY, ez, 0f, 0.25f,
            1f, 0f, 0f, 0.75f, 0.71f, 0.67f
        )
        // Left edge (-X)
        putQuadUV(
            deskOut,
            -ex, botY, -ez, 0f, 0f,
            -ex, botY, ez, maxV, 0f,
            -ex, topY, ez, maxV, 0.25f,
            -ex, topY, -ez, 0f, 0.25f,
            -1f, 0f, 0f, 0.75f, 0.71f, 0.67f
        )

        // 2. Road Ribbon, Kerbs & Bridge Side Walls
        val halfWidth = track.roadWidth * 0.5f
        val kerbWidth = 0.65f
        val kerbHeight = 0.08f
        val roadTile = track.roadTextureTileUnits.coerceAtLeast(8f)
        val n = track.samples.size

        for (i in 0 until n) {
            val s0 = track.samples[i]
            val s1 = track.samples[(i + 1) % n]

            val d0 = s0.distance
            val d1 = if (i == n - 1) track.totalLength else s1.distance
            val rv0 = d0 / roadTile
            val rv1 = d1 / roadTile
            val kv0 = d0 / 3.2f
            val kv1 = d1 / 3.2f

            val bankDy0 = sin(s0.bankAngleRad) * halfWidth * 0.35f
            val bankDy1 = sin(s1.bankAngleRad) * halfWidth * 0.35f

            // v7.3: banked corners pivot about their LOW edge, never dipping below the base height —
            // the low edge used to sink ~0.4 u under the desk, which showed as a sawtooth of desk
            // texture cutting into the asphalt on Toy Box. KartPhysics.bankedSurfaceY matches this.
            val y0L = s0.pos.y + 0.03f + kotlin.math.abs(bankDy0) - bankDy0
            val y0R = s0.pos.y + 0.03f + kotlin.math.abs(bankDy0) + bankDy0
            val y1L = s1.pos.y + 0.03f + kotlin.math.abs(bankDy1) - bankDy1
            val y1R = s1.pos.y + 0.03f + kotlin.math.abs(bankDy1) + bankDy1

            val l0x = s0.pos.x - s0.rightX * halfWidth
            val l0z = s0.pos.z - s0.rightZ * halfWidth
            val r0x = s0.pos.x + s0.rightX * halfWidth
            val r0z = s0.pos.z + s0.rightZ * halfWidth

            val l1x = s1.pos.x - s1.rightX * halfWidth
            val l1z = s1.pos.z - s1.rightZ * halfWidth
            val r1x = s1.pos.x + s1.rightX * halfWidth
            val r1z = s1.pos.z + s1.rightZ * halfWidth

            // Road surface quad
            putQuadUV(
                roadOut,
                l0x, y0L, l0z, 0f, rv0,
                r0x, y0R, r0z, 1f, rv0,
                r1x, y1R, r1z, 1f, rv1,
                l1x, y1L, l1z, 0f, rv1,
                0f, 1f, 0f
            )
            // Per-vertex spline tangent (6 vertices per quad) for D2 road normal mapping
            val t0x = s0.tangentX; val t0z = s0.tangentZ
            val t1x = s1.tangentX; val t1z = s1.tangentZ
            roadTanOut.add(t0x); roadTanOut.add(0f); roadTanOut.add(t0z)
            roadTanOut.add(t0x); roadTanOut.add(0f); roadTanOut.add(t0z)
            roadTanOut.add(t1x); roadTanOut.add(0f); roadTanOut.add(t1z)
            roadTanOut.add(t0x); roadTanOut.add(0f); roadTanOut.add(t0z)
            roadTanOut.add(t1x); roadTanOut.add(0f); roadTanOut.add(t1z)
            roadTanOut.add(t1x); roadTanOut.add(0f); roadTanOut.add(t1z)

            // Raised Left Kerb (top + inner lip)
            val kl0x = l0x - s0.rightX * kerbWidth
            val kl0z = l0z - s0.rightZ * kerbWidth
            val kl1x = l1x - s1.rightX * kerbWidth
            val kl1z = l1z - s1.rightZ * kerbWidth

            putQuadUV(
                kerbOut,
                kl0x, y0L + kerbHeight, kl0z, 0f, kv0,
                l0x, y0L + kerbHeight, l0z, 1f, kv0,
                l1x, y1L + kerbHeight, l1z, 1f, kv1,
                kl1x, y1L + kerbHeight, kl1z, 0f, kv1,
                0f, 1f, 0f
            )
            putQuadUV(
                kerbOut,
                l0x, y0L, l0z, 0f, kv0,
                l1x, y1L, l1z, 0f, kv1,
                l1x, y1L + kerbHeight, l1z, 1f, kv1,
                l0x, y0L + kerbHeight, l0z, 1f, kv0,
                s0.rightX, 0.2f, s0.rightZ
            )

            // Raised Right Kerb (top + inner lip)
            val kr0x = r0x + s0.rightX * kerbWidth
            val kr0z = r0z + s0.rightZ * kerbWidth
            val kr1x = r1x + s1.rightX * kerbWidth
            val kr1z = r1z + s1.rightZ * kerbWidth

            putQuadUV(
                kerbOut,
                r0x, y0R + kerbHeight, r0z, 0f, kv0,
                kr0x, y0R + kerbHeight, kr0z, 1f, kv0,
                kr1x, y1R + kerbHeight, kr1z, 1f, kv1,
                r1x, y1R + kerbHeight, r1z, 0f, kv1,
                0f, 1f, 0f
            )
            putQuadUV(
                kerbOut,
                r1x, y1R, r1z, 0f, kv1,
                r0x, y0R, r0z, 0f, kv0,
                r0x, y0R + kerbHeight, r0z, 1f, kv0,
                r1x, y1R + kerbHeight, r1z, 1f, kv1,
                -s0.rightX, 0.2f, -s0.rightZ
            )

            // Elevated Bridge Side Guardrails & Underside Deck
            if (s0.pos.y > 0.8f || s1.pos.y > 0.8f) {
                val railH = 0.52f
                val bv0 = d0 / 12f
                val bv1 = d1 / 12f
                // Left rail inner wall
                putQuadUV(
                    bridgeOut,
                    kl0x, y0L, kl0z, 0f, bv0,
                    kl1x, y1L, kl1z, 0f, bv1,
                    kl1x, y1L + railH, kl1z, 1f, bv1,
                    kl0x, y0L + railH, kl0z, 1f, bv0,
                    s0.rightX, 0.3f, s0.rightZ,
                    0.92f, 0.82f, 0.65f
                )
                // Right rail inner wall
                putQuadUV(
                    bridgeOut,
                    kr1x, y1R, kr1z, 0f, bv1,
                    kr0x, y0R, kr0z, 0f, bv0,
                    kr0x, y0R + railH, kr0z, 1f, bv0,
                    kr1x, y1R + railH, kr1z, 1f, bv1,
                    -s0.rightX, 0.3f, -s0.rightZ,
                    0.92f, 0.82f, 0.65f
                )
            }
        }

        // 3. Checkered Start/Finish Line Banner across the road at s = 0
        val sStart = track.samples[0]
        val sNext = track.samples[2.coerceAtMost(n - 1)]
        val fy = sStart.pos.y + 0.055f
        val fl0x = sStart.pos.x - sStart.rightX * halfWidth
        val fl0z = sStart.pos.z - sStart.rightZ * halfWidth
        val fr0x = sStart.pos.x + sStart.rightX * halfWidth
        val fr0z = sStart.pos.z + sStart.rightZ * halfWidth
        val fl1x = sNext.pos.x - sNext.rightX * halfWidth
        val fl1z = sNext.pos.z - sNext.rightZ * halfWidth
        val fr1x = sNext.pos.x + sNext.rightX * halfWidth
        val fr1z = sNext.pos.z + sNext.rightZ * halfWidth

        putQuadUV(
            finishOut,
            fl0x, fy, fl0z, 0f, 0f,
            fr0x, fy, fr0z, 4f, 0f,
            fr1x, fy, fr1z, 4f, 1f,
            fl1x, fy, fl1z, 0f, 1f,
            0f, 1f, 0f
        )

        val deskArr = deskOut.toFloatArray()
        val roadArr = roadOut.toFloatArray()
        val roadTanArr = roadTanOut.toFloatArray()
        val kerbArr = kerbOut.toFloatArray()
        val finishArr = finishOut.toFloatArray()
        val bridgeArr = bridgeOut.toFloatArray()

        return TrackRenderMeshes(
            deskSlabMesh = GlMesh(createFloatBuffer(deskArr), deskArr.size / 11),
            roadRibbonMesh = GlMesh(
                buffer = createFloatBuffer(roadArr),
                vertexCount = roadArr.size / 11,
                tangentBuffer = createFloatBuffer(roadTanArr)
            ),
            kerbRibbonMesh = GlMesh(createFloatBuffer(kerbArr), kerbArr.size / 11),
            finishBannerMesh = GlMesh(createFloatBuffer(finishArr), finishArr.size / 11),
            bridgeSupportMesh = GlMesh(createFloatBuffer(bridgeArr), bridgeArr.size / 11)
        )
    }

    fun buildTrackRoadMesh(track: Track): GlMesh {
        return buildTrackMeshes(track).roadRibbonMesh
    }

    fun hexToRgb(hex: Long): FloatArray {
        val r = ((hex shr 16) and 0xFFL) / 255f
        val g = ((hex shr 8) and 0xFFL) / 255f
        val b = (hex and 0xFFL) / 255f
        return floatArrayOf(r, g, b)
    }
}
