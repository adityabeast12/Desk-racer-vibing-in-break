package com.example.render

import com.example.game.RenderKartSnapshot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particles(private val maxParticles: Int = 240) {

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
    }

    private val pool = Array(maxParticles) { Particle() }
    private var nextIndex = 0

    // Each active particle is rendered as 2 triangles (6 vertices * 7 floats: x,y,z, r,g,b,a)
    private val vertexData = FloatArray(maxParticles * 6 * 7)
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
        for (kart in karts) {
            val backX = kart.x - sin(kart.headingRad) * 1.15f
            val backZ = kart.z - cos(kart.headingRad) * 1.15f

            // 1. Drift Sparks (Blue / Orange / Purple)
            if (kart.sparkTier > 0) {
                val (sr, sg, sb) = when (kart.sparkTier) {
                    3 -> Triple(0.75f, 0.25f, 1.0f)  // Purple Ultra Sparks
                    2 -> Triple(1.0f, 0.58f, 0.08f)  // Orange Super Sparks
                    else -> Triple(0.22f, 0.78f, 1.0f) // Blue Mini Sparks
                }
                repeat(if (allowFullParticles) 2 else 1) {
                    spawn(
                        x = backX + (Random.nextFloat() - 0.5f) * 0.75f,
                        y = kart.y + 0.18f,
                        z = backZ + (Random.nextFloat() - 0.5f) * 0.75f,
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
        if (celebrationConfettiActive && focusKart != null) {
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
        size: Float, maxLife: Float
    ) {
        val p = pool[nextIndex]
        nextIndex = (nextIndex + 1) % maxParticles
        p.active = true
        p.x = x; p.y = y; p.z = z
        p.vx = vx; p.vy = vy; p.vz = vz
        p.r = r; p.g = g; p.b = b; p.a = 0.95f
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
            p.x += p.vx * dt
            p.y = (p.y + p.vy * dt).coerceAtLeast(0.05f)
            p.z += p.vz * dt
            p.vy -= 9.8f * dt
            val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
            val s = p.size * (0.5f + 0.5f * alpha)

            val x0 = p.x - s; val x1 = p.x + s
            val y0 = p.y - s; val y1 = p.y + s
            val z = p.z

            fun putV(vx: Float, vy: Float, vz: Float) {
                vertexData[floatCursor++] = vx
                vertexData[floatCursor++] = vy
                vertexData[floatCursor++] = vz
                vertexData[floatCursor++] = p.r
                vertexData[floatCursor++] = p.g
                vertexData[floatCursor++] = p.b
                vertexData[floatCursor++] = alpha
                vertCount++
            }

            putV(x0, y0, z); putV(x1, y0, z); putV(x1, y1, z)
            putV(x0, y0, z); putV(x1, y1, z); putV(x0, y1, z)
        }

        activeVertexCount = vertCount
        if (vertCount > 0) {
            vertexBuffer.position(0)
            vertexBuffer.put(vertexData, 0, floatCursor)
            vertexBuffer.position(0)
        }
    }
}
