package com.example.render

import com.example.game.RenderKartSnapshot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particles(private val maxParticles: Int = 480) {

    private companion object {
        const val SEGMENTS = 6
        val COS = FloatArray(SEGMENTS + 1) { cos(it * 2.0 * Math.PI / SEGMENTS).toFloat() }
        val SIN = FloatArray(SEGMENTS + 1) { sin(it * 2.0 * Math.PI / SEGMENTS).toFloat() }
    }

    private class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var z = 0f
        var vx = 0f
        var vy = 0f
        var vz = 0f
        var r = 1f
        var g = 1f
        var b = 1f
        var a = 1f
        var size = 0.22f
        var life = 0f
        var maxLife = 0.4f
        var gravity = 9.8f
        var grow = 0f      // >0: puff expands over its life (smoke); 0: shrinks (sparks)
        var drag = 0f
    }

    // v7: camera-facing billboards (quads used to be fixed in the world XY plane, so seen from
    // behind a kart heading along X they were edge-on and vanished)
    private var rX = 1f; private var rY = 0f; private var rZ = 0f
    private var uX = 0f; private var uY = 1f; private var uZ = 0f

    fun setBillboardBasis(rightX: Float, rightY: Float, rightZ: Float, upX: Float, upY: Float, upZ: Float) {
        rX = rightX; rY = rightY; rZ = rightZ; uX = upX; uY = upY; uZ = upZ
    }

    private val pool = Array(maxParticles) { Particle() }
    private var nextIndex = 0

    // Each active particle is rendered as 2 triangles (6 vertices * 7 floats: x,y,z, r,g,b,a)
    private val vertexData = FloatArray(maxParticles * SEGMENTS * 3 * 7)
    val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    var activeVertexCount: Int = 0
        private set

    private val confettiColors = arrayOf(
        floatArrayOf(1.0f, 0.20f, 0.42f),
        floatArrayOf(0.0f, 0.96f, 0.83f),
        floatArrayOf(0.98f, 0.80f, 0.08f),
        floatArrayOf(0.22f, 0.74f, 0.98f),
        floatArrayOf(0.66f, 0.33f, 0.97f),
        floatArrayOf(0.13f, 0.85f, 0.42f)
    )

    fun emitForKarts(
        dt: Float,
        karts: List<RenderKartSnapshot>,
        allowFullParticles: Boolean,
        celebrationConfettiActive: Boolean = false,
        focusKart: RenderKartSnapshot? = null
    ) {
        // v6: emission was per draw call, so 90/120 Hz screens spawned 1.5-2x the particles.
        // Gate emission to a 60 Hz-equivalent rate regardless of refresh rate.
        val emitThisFrame = Random.nextFloat() < (dt * 60f).coerceAtMost(1f)
        for (kart in karts) {
            if (!emitThisFrame) break
            val backX = kart.x - sin(kart.headingRad) * 1.15f
            val backZ = kart.z - cos(kart.headingRad) * 1.15f
            val sideX = cos(kart.headingRad)
            val sideZ = -sin(kart.headingRad)

            // 1. Drift Sparks (Blue / Orange / Purple)
            if (kart.sparkTier > 0) {
                val (sr, sg, sb) = when (kart.sparkTier) {
                    3 -> Triple(0.75f, 0.25f, 1.0f)  // Purple Ultra Sparks
                    2 -> Triple(1.0f, 0.58f, 0.08f)  // Orange Super Sparks
                    else -> Triple(0.22f, 0.78f, 1.0f) // Blue Mini Sparks
                }
                repeat(if (allowFullParticles) 2 else 1) { n ->
                    val side = if (n % 2 == 0) 0.72f else -0.72f
                    spawn(
                        x = backX + sideX * side + (Random.nextFloat() - 0.5f) * 0.25f,
                        y = kart.y + 0.14f,
                        z = backZ + sideZ * side + (Random.nextFloat() - 0.5f) * 0.25f,
                        vx = (Random.nextFloat() - 0.5f) * 4.5f,
                        vy = Random.nextFloat() * 3.8f + 1.0f,
                        vz = (Random.nextFloat() - 0.5f) * 4.5f,
                        r = sr, g = sg, b = sb,
                        size = 0.24f,
                        maxLife = 0.28f
                    )
                }
            }

            // 2. Rocket Start Stall Smoke Puff (Part D2)
            if (kart.rocketStallSmoke) {
                repeat(2) {
                    spawn(
                        x = backX + (Random.nextFloat() - 0.5f) * 0.8f,
                        y = kart.y + 0.35f,
                        z = backZ + (Random.nextFloat() - 0.5f) * 0.8f,
                        vx = (Random.nextFloat() - 0.5f) * 3.0f,
                        vy = Random.nextFloat() * 3.2f + 1.2f,
                        vz = (Random.nextFloat() - 0.5f) * 3.0f,
                        r = 0.35f, g = 0.37f, b = 0.42f,
                        size = 0.42f,
                        maxLife = 0.48f
                    )
                }
            }

            if (!allowFullParticles) continue

            // 2b. Drift tyre smoke from the rear wheels (grows and rises, no gravity)
            if (kart.isDrifting && abs(kart.speed) > 8f && Random.nextFloat() < 0.8f) {
                val side = if (Random.nextBoolean()) 0.72f else -0.72f
                spawn(
                    x = backX + sideX * side, y = kart.y + 0.2f, z = backZ + sideZ * side,
                    vx = (Random.nextFloat() - 0.5f) * 1.2f, vy = 0.6f + Random.nextFloat() * 0.6f, vz = (Random.nextFloat() - 0.5f) * 1.2f,
                    r = 0.80f, g = 0.80f, b = 0.82f, size = 0.46f, maxLife = 0.7f,
                    gravity = -0.4f, grow = 2.2f, drag = 2.5f, alphaScale = 0.42f
                )
            }

            // 2c. Slipstream wind streaks
            if (kart.draftLevel > 0.2f && Random.nextFloat() < kart.draftLevel) {
                val fx = sin(kart.headingRad); val fz = cos(kart.headingRad)
                val lat = (Random.nextFloat() - 0.5f) * 2.2f
                spawn(
                    x = kart.x + fx * 1.6f + sideX * lat, y = kart.y + 0.4f + Random.nextFloat() * 0.8f, z = kart.z + fz * 1.6f + sideZ * lat,
                    vx = -fx * 16f, vy = 0f, vz = -fz * 16f,
                    r = 0.92f, g = 0.96f, b = 1f, size = 0.07f, maxLife = 0.22f,
                    gravity = 0f, alphaScale = 0.7f
                )
            }

            // 3. Boost Exhaust Flames
            if (kart.isBoosting) {
                repeat(2) {
                    spawn(
                        x = backX + (Random.nextFloat() - 0.5f) * 0.5f,
                        y = kart.y + 0.36f,
                        z = backZ + (Random.nextFloat() - 0.5f) * 0.5f,
                        vx = -sin(kart.headingRad) * 6f + (Random.nextFloat() - 0.5f) * 2f,
                        vy = (Random.nextFloat() - 0.3f) * 2f,
                        vz = -cos(kart.headingRad) * 6f + (Random.nextFloat() - 0.5f) * 2f,
                        r = 1.0f, g = 0.45f, b = 0.08f,
                        size = 0.32f,
                        maxLife = 0.22f
                    )
                }
            }

            // 4. Off-Track Paper Dust
            if (kart.isOffTrack && abs(kart.speed) > 5f && Random.nextFloat() < 0.45f) {
                spawn(
                    x = backX + (Random.nextFloat() - 0.5f) * 0.9f,
                    y = kart.y + 0.15f,
                    z = backZ + (Random.nextFloat() - 0.5f) * 0.9f,
                    vx = (Random.nextFloat() - 0.5f) * 2f,
                    vy = Random.nextFloat() * 1.8f + 0.5f,
                    vz = (Random.nextFloat() - 0.5f) * 2f,
                    r = 0.85f, g = 0.82f, b = 0.75f,
                    size = 0.28f,
                    maxLife = 0.35f
                )
            }
        }

        // 5. Finish Line Celebration Confetti (Part D5)
        if (celebrationConfettiActive && focusKart != null && emitThisFrame) {
            repeat(if (allowFullParticles) 4 else 2) {
                val c = confettiColors[Random.nextInt(confettiColors.size)]
                spawn(
                    x = focusKart.x + (Random.nextFloat() - 0.5f) * 9.0f,
                    y = focusKart.y + 4.5f + Random.nextFloat() * 2.5f,
                    z = focusKart.z + (Random.nextFloat() - 0.5f) * 9.0f,
                    vx = (Random.nextFloat() - 0.5f) * 3.5f,
                    vy = -1.5f + Random.nextFloat() * 2.0f,
                    vz = (Random.nextFloat() - 0.5f) * 3.5f,
                    r = c[0], g = c[1], b = c[2],
                    size = 0.26f,
                    maxLife = 0.85f
                )
            }
        }

        stepAndBuildMesh(dt)
    }

    private fun spawn(
        x: Float, y: Float, z: Float,
        vx: Float, vy: Float, vz: Float,
        r: Float, g: Float, b: Float,
        size: Float, maxLife: Float,
        gravity: Float = 9.8f, grow: Float = 0f, drag: Float = 0f, alphaScale: Float = 0.95f
    ) {
        val p = pool[nextIndex]
        nextIndex = (nextIndex + 1) % maxParticles
        p.active = true
        p.x = x; p.y = y; p.z = z
        p.vx = vx; p.vy = vy; p.vz = vz
        p.r = r; p.g = g; p.b = b; p.a = alphaScale
        p.gravity = gravity; p.grow = grow; p.drag = drag
        p.size = size
        p.life = maxLife
        p.maxLife = maxLife
    }

    private fun stepAndBuildMesh(dt: Float) {
        var floatCursor = 0
        var vertCount = 0

        for (p in pool) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.active = false
                continue
            }
            if (p.drag > 0f) {
                val k = (1f - p.drag * dt).coerceIn(0f, 1f)
                p.vx *= k; p.vz *= k
            }
            p.x += p.vx * dt
            p.y = (p.y + p.vy * dt).coerceAtLeast(0.05f)
            p.z += p.vz * dt
            p.vy -= p.gravity * dt
            val lifeFrac = (p.life / p.maxLife).coerceIn(0f, 1f)
            val alpha = lifeFrac * p.a
            val s = if (p.grow > 0f) p.size * (1f + p.grow * (1f - lifeFrac)) else p.size * (0.5f + 0.5f * lifeFrac)
            val ax = rX * s; val ay = rY * s; val az = rZ * s
            val bx = uX * s; val by = uY * s; val bz = uZ * s

            fun putV(vx: Float, vy: Float, vz: Float, a: Float) {
                vertexData[floatCursor++] = vx
                vertexData[floatCursor++] = vy
                vertexData[floatCursor++] = vz
                vertexData[floatCursor++] = p.r
                vertexData[floatCursor++] = p.g
                vertexData[floatCursor++] = p.b
                vertexData[floatCursor++] = a
                vertCount++
            }

            // v7.3: soft round puff — a 6-segment fan with full alpha in the centre fading to the rim
            // (flat quads read as grey squares, especially the drift smoke)
            val px = p.x; val py = p.y; val pz = p.z
            val rimA = if (p.grow > 0f) 0f else alpha * 0.25f
            for (k in 0 until SEGMENTS) {
                val c0 = COS[k]; val s0 = SIN[k]; val c1 = COS[k + 1]; val s1 = SIN[k + 1]
                putV(px, py, pz, alpha)
                putV(px + ax * c0 + bx * s0, py + ay * c0 + by * s0, pz + az * c0 + bz * s0, rimA)
                putV(px + ax * c1 + bx * s1, py + ay * c1 + by * s1, pz + az * c1 + bz * s1, rimA)
            }
        }

        activeVertexCount = vertCount
        if (vertCount > 0) {
            vertexBuffer.position(0)
            vertexBuffer.put(vertexData, 0, floatCursor)
            vertexBuffer.position(0)
        }
    }
}
