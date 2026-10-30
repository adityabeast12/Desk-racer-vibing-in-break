package com.example.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.DynamicHazardKind
import com.example.game.GameConstants
import com.example.game.GameMode
import com.example.game.KartBodyType
import com.example.game.ProjectileKind
import com.example.game.PropKind
import com.example.game.RenderDynamicHazardSnapshot
import com.example.game.RenderGhostSnapshot
import com.example.game.RenderKartSnapshot
import com.example.game.SplineSample
import com.example.game.TrackProp
import com.example.game.Tracks
import com.example.game.WorldRenderSnapshot
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class GameRenderer(
    private val snapshotRef: AtomicReference<WorldRenderSnapshot>,
    private val appContext: Context? = null
) : GLSurfaceView.Renderer {

    @Volatile
    var allowHighParticles: Boolean = true

    private val camera = Camera()
    private val particles = Particles(maxParticles = 240)

    private var litProgram = 0
    private var litPosLoc = 0
    private var litNormLoc = 0
    private var litVertColorLoc = 0
    private var litPaintMaskLoc = 0
    private var litMvpLoc = 0
    private var litModelLoc = 0
    private var litPaintColorLoc = 0
    private var litTintLoc = 0
    private var litLightDirLoc = 0
    private var litFogColorLoc = 0
    private var litCamPosLoc = 0

    private var unlitProgram = 0
    private var unlitPosLoc = 0
    private var unlitColorLoc = 0
    private var unlitMvpLoc = 0
    private var unlitTintLoc = 0

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val partMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private lateinit var unitCube: GlMesh
    private lateinit var rainbowCube: GlMesh
    private lateinit var unitSphere: GlMesh
    private lateinit var wheelCylinder: GlMesh
    private val trackMeshes = HashMap<Int, GlMesh>()

    // Curated DRM1 3D models loaded from assets/models/*.drm with procedural fallback
    private val kartDrmModels = HashMap<KartBodyType, DrmModel>()
    private var coinDrmModel: DrmModel? = null
    private var blockCoinDrmModel: DrmModel? = null
    private var flagDrmModel: DrmModel? = null
    private var cloudDrmModel: DrmModel? = null

    private var viewportWidth = 1280
    private var viewportHeight = 720
    private var surfaceFrameCount = 0
    private var drmAssetsLoaded = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        litProgram = Shaders.compileProgram(Shaders.LIT_VERTEX_SHADER, Shaders.LIT_FRAGMENT_SHADER)
        litPosLoc = GLES20.glGetAttribLocation(litProgram, "aPosition")
        litNormLoc = GLES20.glGetAttribLocation(litProgram, "aNormal")
        litVertColorLoc = GLES20.glGetAttribLocation(litProgram, "aVertColor")
        litPaintMaskLoc = GLES20.glGetAttribLocation(litProgram, "aPaintMask")
        litMvpLoc = GLES20.glGetUniformLocation(litProgram, "uMvpMatrix")
        litModelLoc = GLES20.glGetUniformLocation(litProgram, "uModelMatrix")
        litPaintColorLoc = GLES20.glGetUniformLocation(litProgram, "uPaintColor")
        litTintLoc = GLES20.glGetUniformLocation(litProgram, "uTintColor")
        litLightDirLoc = GLES20.glGetUniformLocation(litProgram, "uLightDir")
        litFogColorLoc = GLES20.glGetUniformLocation(litProgram, "uFogColor")
        litCamPosLoc = GLES20.glGetUniformLocation(litProgram, "uCameraPos")

        unlitProgram = Shaders.compileProgram(Shaders.UNLIT_VERTEX_SHADER, Shaders.UNLIT_FRAGMENT_SHADER)
        unlitPosLoc = GLES20.glGetAttribLocation(unlitProgram, "aPosition")
        unlitColorLoc = GLES20.glGetAttribLocation(unlitProgram, "aColor")
        unlitMvpLoc = GLES20.glGetUniformLocation(unlitProgram, "uMvpMatrix")
        unlitTintLoc = GLES20.glGetUniformLocation(unlitProgram, "uTintColor")

        unitCube = Meshes.buildUnitCube()
        rainbowCube = Meshes.buildRainbowItemCube()
        unitSphere = Meshes.buildSphere(10, 14)
        wheelCylinder = Meshes.buildWheelCylinder(14)

        // Defer track road meshes and DRM asset parsing until after frame 1 swaps
        // so SurfaceSyncGroup never waits >1000ms for the initial SurfaceView transaction.
        trackMeshes.clear()
        kartDrmModels.clear()
        coinDrmModel = null
        blockCoinDrmModel = null
        flagDrmModel = null
        cloudDrmModel = null
        surfaceFrameCount = 0
        drmAssetsLoaded = false
    }

    private fun loadDrmAssetsIfNeeded() {
        if (drmAssetsLoaded) return
        drmAssetsLoaded = true
        val ctx = appContext ?: return
        for (body in KartBodyType.entries) {
            DrmModelLoader.loadFromAssets(ctx, body.drmAssetPath)?.let {
                kartDrmModels[body] = it
            }
        }
        coinDrmModel = DrmModelLoader.loadFromAssets(ctx, "models/coin.drm")
        blockCoinDrmModel = DrmModelLoader.loadFromAssets(ctx, "models/block_coin.drm")
        flagDrmModel = DrmModelLoader.loadFromAssets(ctx, "models/flag.drm")
        cloudDrmModel = DrmModelLoader.loadFromAssets(ctx, "models/cloud.drm")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        surfaceFrameCount++
        val snap = snapshotRef.get()
        if (snap == null) {
            GLES20.glClearColor(0.06f, 0.09f, 0.16f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            return
        }
        val track = Tracks.byId(snap.trackId)
        val fogRgb = Meshes.hexToRgb(track.fogHex)

        GLES20.glClearColor(fogRgb[0], fogRgb[1], fogRgb[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (litProgram == 0) return

        // Complete the very first frame immediately so WindowManager SurfaceSyncGroup finishes in <10ms
        if (surfaceFrameCount == 1) {
            return
        }
        if (surfaceFrameCount >= 3 && !drmAssetsLoaded) {
            loadDrmAssetsIfNeeded()
        }

        val localKart = snap.karts.firstOrNull { it.slotIndex == snap.localSlotIndex }
            ?: snap.karts.firstOrNull()

        camera.updateChase(
            dt = GameConstants.SIM_DT,
            targetKart = localKart,
            phase = snap.phase,
            track = track,
            flybyProgress01 = snap.flybyProgress,
            showroomAngleRad = snap.showroomCameraAngleRad,
            landingShakeSignal = snap.landingShakeSignal
        )

        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, camera.currentFovDeg, aspect, 0.5f, 260f)
        Matrix.setLookAtM(
            viewMatrix, 0,
            camera.posX, camera.posY, camera.posZ,
            camera.lookX, camera.lookY, camera.lookZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES20.glUseProgram(litProgram)
        GLES20.glUniform3f(litLightDirLoc, 0.42f, 0.85f, 0.32f)
        GLES20.glUniform3f(litFogColorLoc, fogRgb[0], fogRgb[1], fogRgb[2])
        GLES20.glUniform3f(litCamPosLoc, camera.posX, camera.posY, camera.posZ)
        GLES20.glUniform3f(litPaintColorLoc, 1f, 1f, 1f)
        if (litPaintMaskLoc >= 0) {
            GLES20.glDisableVertexAttribArray(litPaintMaskLoc)
            GLES20.glVertexAttrib1f(litPaintMaskLoc, 0f)
        }

        // 1. Draw Desk Tabletop
        val deskRgb = Meshes.hexToRgb(track.deskSurfaceHex)
        drawLitMesh(
            mesh = unitCube,
            x = 0f, y = -1.2f, z = 0f,
            sx = track.deskHalfExtentX * 2f, sy = 2.4f, sz = track.deskHalfExtentZ * 2f,
            r = deskRgb[0], g = deskRgb[1], b = deskRgb[2], a = 1f
        )

        // Kitchen Counter Dark Sink Gap Cutout
        if (track.hasSinkGapZone) {
            val cx = (track.sinkGapMinX + track.sinkGapMaxX) * 0.5f
            val cz = (track.sinkGapMinZ + track.sinkGapMaxZ) * 0.5f
            val sx = track.sinkGapMaxX - track.sinkGapMinX
            val sz = track.sinkGapMaxZ - track.sinkGapMinZ
            drawLitMesh(unitCube, cx, 0.02f, cz, sx, 0.06f, sz, 0.03f, 0.05f, 0.08f, 1f)
        }

        // Art Desk Paint Puddles
        for (puddle in track.paintPuddles) {
            val prgb = Meshes.hexToRgb(puddle.tintColorHex)
            drawLitMesh(
                unitSphere,
                x = puddle.x, y = 0.04f, z = puddle.z,
                sx = puddle.radius * 2f, sy = 0.05f, sz = puddle.radius * 2f,
                r = prgb[0], g = prgb[1], b = prgb[2], a = 0.90f
            )
        }

        // Bathtub Rim Soap Slippery Zones
        for (soap in track.slipperyZones) {
            drawLitMesh(
                unitSphere,
                x = soap.x, y = 0.04f, z = soap.z,
                sx = soap.radius * 2f, sy = 0.05f, sz = soap.radius * 2f,
                r = 0.72f, g = 0.92f, b = 1.0f, a = 0.72f
            )
        }

        // 2. Draw Spline Track Ribbon (Road, Kerbs, Bridge Walls, Checkered Start) — built lazily per track
        val roadMesh = trackMeshes.getOrPut(track.id) {
            Meshes.buildTrackRoadMesh(track)
        }
        Matrix.setIdentityM(modelMatrix, 0)
        renderLitMeshBuffer(roadMesh, 1f, 1f, 1f, 1f)

        // Elevated Track Support Columns
        for (i in track.samples.indices step 10) {
            val s = track.samples[i]
            if (s.pos.y > 1.2f) {
                val colH = s.pos.y
                drawLitMesh(
                    unitCube,
                    x = s.pos.x, y = colH * 0.5f, z = s.pos.z,
                    sx = 1.2f, sy = colH, sz = 1.2f,
                    r = 0.45f, g = 0.48f, b = 0.54f, a = 1f
                )
            }
        }

        // 3. Start/Finish Overhead Gantry + 3 Countdown Lights + Flags (Part D2)
        drawStartFinishGantry(track.samples.first(), track.roadWidth, snap.countdownLightsLit)

        // 4. Draw Track Props
        for (prop in track.props) {
            drawTrackProp(prop)
        }

        // 5. Draw Dynamic Hazards (Peas, Scissors Gate, Spinning Top)
        for (haz in snap.dynamicHazards) {
            drawDynamicHazard(haz)
        }

        // 6. Draw Active Gold Coins (Part D6)
        for (coin in snap.coins) {
            if (!coin.active) continue
            val deg = Math.toDegrees(coin.rotationRad.toDouble()).toFloat()
            val drmCoin = coinDrmModel
            if (drmCoin != null) {
                drawDrmModelSimple(
                    model = drmCoin,
                    x = coin.x, y = coin.y, z = coin.z,
                    yawDeg = deg,
                    scale = 2.4f,
                    paintRgb = floatArrayOf(1f, 0.84f, 0.1f),
                    alpha = 1f
                )
            } else {
                drawLitMeshRotated(
                    unitSphere,
                    x = coin.x, y = coin.y + 0.35f, z = coin.z,
                    rxDeg = 0f, ryDeg = deg, rzDeg = 0f,
                    sx = 0.85f, sy = 0.85f, sz = 0.25f,
                    r = 1.0f, g = 0.84f, b = 0.08f, a = 1f
                )
            }
        }

        // 7. Draw Floating, Spinning Item Boxes
        for (box in snap.itemBoxes) {
            if (!box.active) continue
            val deg = Math.toDegrees(box.rotationRad.toDouble()).toFloat()
            val bobY = box.y + sin(box.rotationRad * 2f) * 0.18f
            val drmBox = blockCoinDrmModel
            if (drmBox != null) {
                drawDrmModelSimple(
                    model = drmBox,
                    x = box.x, y = bobY - 0.35f, z = box.z,
                    yawDeg = deg,
                    scale = 1.9f,
                    paintRgb = floatArrayOf(1f, 1f, 1f),
                    alpha = 0.92f
                )
            } else {
                drawLitMeshRotated(
                    rainbowCube,
                    x = box.x, y = bobY, z = box.z,
                    rxDeg = deg * 0.6f, ryDeg = deg, rzDeg = 25f,
                    sx = 1.15f, sy = 1.15f, sz = 1.15f,
                    r = 1f, g = 1f, b = 1f, a = 0.88f
                )
            }
        }

        // 8. Draw Time Trial Ghost Kart (translucent 42% alpha)
        val ghost = snap.ghostKart
        if (ghost != null) {
            drawGhostKart3D(ghost)
        }

        // 9. Draw All Karts
        val isBattle = snap.gameMode == GameMode.BATTLE_BALLOONS
        for (kart in snap.karts) {
            if (!kart.isInvulnerableFlashing) {
                val alpha = if (kart.isGhostMode) 0.50f else 1.0f
                drawKart3D(kart, forceAlpha = alpha, isBattleMode = isBattle)
            }
        }

        // 10. Draw Active Projectiles (Marbles, Erasers, Tacks, Paper Planes)
        for (proj in snap.projectiles) {
            when (proj.kind) {
                ProjectileKind.MARBLE -> {
                    drawLitMesh(unitSphere, proj.x, proj.y, proj.z, 0.65f, 0.65f, 0.65f, 0.13f, 0.85f, 0.42f, 1f)
                }
                ProjectileKind.ERASER -> {
                    drawLitMesh(unitCube, proj.x, proj.y, proj.z, 1.3f, 0.55f, 0.85f, 0.96f, 0.35f, 0.55f, 1f)
                }
                ProjectileKind.TACK -> {
                    drawLitMesh(unitSphere, proj.x, proj.y, proj.z, 0.48f, 0.48f, 0.48f, 0.98f, 0.82f, 0.12f, 1f)
                }
                ProjectileKind.PAPER_PLANE -> {
                    val yawDeg = Math.toDegrees(proj.headingRad.toDouble()).toFloat()
                    drawLitMeshRotated(
                        unitCube, proj.x, proj.y, proj.z,
                        0f, yawDeg, 0f,
                        1.4f, 0.15f, 1.8f,
                        0.96f, 0.98f, 1.0f, 1f
                    )
                }
            }
        }

        // Bathtub Rim translucent water plane (Track ID 5)
        if (track.id == 5) {
            drawLitMesh(
                unitCube,
                x = 0f, y = -0.35f, z = 0f,
                sx = track.deskHalfExtentX * 1.6f, sy = 0.08f, sz = track.deskHalfExtentZ * 1.6f,
                r = 0.22f, g = 0.78f, b = 0.98f, a = 0.46f
            )
        }

        // 11. Update & Draw Pooled Unlit Particles (Sparks, Flames, Stall Smoke, Confetti, Dust)
        particles.emitForKarts(
            dt = GameConstants.SIM_DT,
            karts = snap.karts,
            allowFullParticles = allowHighParticles,
            celebrationConfettiActive = snap.celebrationConfettiActive,
            focusKart = localKart
        )
        if (particles.activeVertexCount > 0) {
            GLES20.glUseProgram(unlitProgram)
            GLES20.glUniformMatrix4fv(unlitMvpLoc, 1, false, vpMatrix, 0)
            GLES20.glUniform4f(unlitTintLoc, 1f, 1f, 1f, 1f)

            val stride = 7 * 4
            val buf = particles.vertexBuffer
            buf.position(0)
            GLES20.glEnableVertexAttribArray(unlitPosLoc)
            GLES20.glVertexAttribPointer(unlitPosLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

            buf.position(3)
            GLES20.glEnableVertexAttribArray(unlitColorLoc)
            GLES20.glVertexAttribPointer(unlitColorLoc, 4, GLES20.GL_FLOAT, false, stride, buf)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, particles.activeVertexCount)
        }
    }

    private fun drawStartFinishGantry(s0: SplineSample, roadWidth: Float, countdownLightsLit: Int) {
        val sYawDeg = Math.toDegrees(s0.headingRad.toDouble()).toFloat()
        val halfW = roadWidth * 0.5f + 1.15f
        val leftX = s0.pos.x - s0.rightX * halfW
        val leftZ = s0.pos.z - s0.rightZ * halfW
        val rightX = s0.pos.x + s0.rightX * halfW
        val rightZ = s0.pos.z + s0.rightZ * halfW

        drawLitMesh(
            unitCube,
            x = leftX, y = s0.pos.y + 3.0f, z = leftZ,
            sx = 0.7f, sy = 6.0f, sz = 0.7f,
            r = 0.95f, g = 0.18f, b = 0.36f, a = 1f
        )
        drawLitMesh(
            unitCube,
            x = rightX, y = s0.pos.y + 3.0f, z = rightZ,
            sx = 0.7f, sy = 6.0f, sz = 0.7f,
            r = 0.95f, g = 0.18f, b = 0.36f, a = 1f
        )
        drawLitMeshRotated(
            unitCube,
            x = s0.pos.x, y = s0.pos.y + 5.6f, z = s0.pos.z,
            rxDeg = 0f, ryDeg = sYawDeg, rzDeg = 0f,
            sx = halfW * 2.15f, sy = 0.95f, sz = 0.75f,
            r = 0.14f, g = 0.18f, b = 0.26f, a = 1f
        )

        // 3 Countdown Gantry Lights (Red 1..3 -> All Green on 4)
        for (lightIdx in 0..2) {
            val offsetLat = (lightIdx - 1) * 2.2f
            val lx = s0.pos.x + s0.rightX * offsetLat
            val lz = s0.pos.z + s0.rightZ * offsetLat
            val isGreen = countdownLightsLit >= 4
            val isRedLit = countdownLightsLit in (lightIdx + 1)..3
            val (lr, lg, lb) = when {
                isGreen -> Triple(0.10f, 0.98f, 0.42f)
                isRedLit -> Triple(1.0f, 0.15f, 0.22f)
                else -> Triple(0.25f, 0.28f, 0.32f)
            }
            drawLitMesh(
                unitSphere,
                x = lx, y = s0.pos.y + 4.9f, z = lz,
                sx = 0.85f, sy = 0.85f, sz = 0.85f,
                r = lr, g = lg, b = lb, a = 1f
            )
        }

        val drmFlag = flagDrmModel
        if (drmFlag != null) {
            drawDrmModelSimple(drmFlag, leftX, s0.pos.y + 6.0f, leftZ, sYawDeg, 2.2f, floatArrayOf(1f, 0.8f, 0.1f), 1f)
            drawDrmModelSimple(drmFlag, rightX, s0.pos.y + 6.0f, rightZ, sYawDeg, 2.2f, floatArrayOf(0f, 0.95f, 0.82f), 1f)
        }
    }

    private fun drawDynamicHazard(haz: RenderDynamicHazardSnapshot) {
        val c = Meshes.hexToRgb(haz.colorHex)
        val yawDeg = Math.toDegrees(haz.rotationRad.toDouble()).toFloat()
        when (haz.kind) {
            DynamicHazardKind.ROLLING_PEA -> {
                drawLitMesh(unitSphere, haz.x, haz.y, haz.z, haz.radius * 2f, haz.radius * 2f, haz.radius * 2f, c[0], c[1], c[2], 1f)
            }
            DynamicHazardKind.SCISSORS_GATE -> {
                drawLitMeshRotated(
                    unitCube, haz.x, haz.y + 0.8f, haz.z,
                    0f, yawDeg, 25f,
                    haz.radius * 2.4f, 1.8f, 0.35f,
                    c[0], c[1], c[2], 1f
                )
            }
            DynamicHazardKind.SPINNING_TOP -> {
                drawLitMeshRotated(
                    unitSphere, haz.x, haz.y + 0.6f, haz.z,
                    12f, yawDeg, 0f,
                    haz.radius * 2.1f, 1.4f, haz.radius * 2.1f,
                    c[0], c[1], c[2], 1f
                )
            }
        }
    }

    private fun drawTrackProp(prop: TrackProp) {
        val c1 = Meshes.hexToRgb(prop.colorHex)
        val c2 = Meshes.hexToRgb(prop.secondaryHex)
        val yawDeg = Math.toDegrees(prop.yawRad.toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(prop.pitchRad.toDouble()).toFloat()

        when (prop.kind) {
            PropKind.BOOK, PropKind.TOY_BLOCK, PropKind.JACK_IN_BOX -> {
                drawLitMeshRotated(unitCube, prop.x, prop.y + prop.sy * 0.5f, prop.z, 0f, yawDeg, 0f, prop.sx - 0.4f, prop.sy - 0.3f, prop.sz - 0.4f, 0.95f, 0.96f, 0.98f, 1f)
                drawLitMeshRotated(unitCube, prop.x, prop.y + prop.sy - 0.12f, prop.z, 0f, yawDeg, 0f, prop.sx, 0.25f, prop.sz, c1[0], c1[1], c1[2], 1f)
                drawLitMeshRotated(unitCube, prop.x, prop.y + 0.12f, prop.z, 0f, yawDeg, 0f, prop.sx, 0.25f, prop.sz, c2[0], c2[1], c2[2], 1f)
            }
            PropKind.MUG_ROUNDABOUT, PropKind.KETTLE_PILLAR, PropKind.SHAMPOO_BOTTLE, PropKind.GLUE_BOLLARD -> {
                drawLitMesh(unitSphere, prop.x, prop.y + prop.sy * 0.45f, prop.z, prop.sx * 0.5f, prop.sy * 0.5f, prop.sz * 0.5f, c1[0], c1[1], c1[2], 1f)
                drawLitMesh(unitCube, prop.x + prop.sx * 0.52f, prop.y + prop.sy * 0.45f, prop.z, 1.2f, prop.sy * 0.5f, 0.8f, c2[0], c2[1], c2[2], 1f)
            }
            PropKind.ORANGE_BUMPER, PropKind.RUBBER_DUCK -> {
                drawLitMesh(unitSphere, prop.x, prop.y + prop.sy * 0.45f, prop.z, prop.sx * 0.5f, prop.sy * 0.5f, prop.sz * 0.5f, c1[0], c1[1], c1[2], 1f)
            }
            PropKind.RULER_RAMP, PropKind.LAPTOP_BRIDGE, PropKind.CUTTING_BOARD, PropKind.WRENCH_ARCH -> {
                drawLitMeshRotated(
                    unitCube, prop.x, prop.y + prop.sy * 0.35f, prop.z,
                    pitchDeg, yawDeg, 0f,
                    prop.sx, prop.sy * 0.3f, prop.sz,
                    c1[0], c1[1], c1[2], 1f
                )
            }
            PropKind.PENNANT_FLAG -> {
                val drmFlag = flagDrmModel
                if (drmFlag != null) {
                    drawDrmModelSimple(drmFlag, prop.x, prop.y, prop.z, yawDeg, prop.sy * 0.45f, c1, 1f)
                } else {
                    drawLitMeshRotated(unitCube, prop.x, prop.y + prop.sy * 0.5f, prop.z, 0f, yawDeg, 0f, prop.sx, prop.sy, prop.sz, c1[0], c1[1], c1[2], 1f)
                }
            }
            PropKind.STEAM_CLOUD -> {
                val drmCloud = cloudDrmModel
                if (drmCloud != null) {
                    drawDrmModelSimple(drmCloud, prop.x, prop.y, prop.z, yawDeg, prop.sx * 0.45f, c1, 0.82f)
                } else {
                    drawLitMesh(unitSphere, prop.x, prop.y + prop.sy * 0.5f, prop.z, prop.sx * 0.5f, prop.sy * 0.5f, prop.sz * 0.5f, c1[0], c1[1], c1[2], 0.80f)
                }
            }
            PropKind.PENCIL_RAIL, PropKind.STICKY_BOOST_PAD, PropKind.KEYBOARD_BUMP, PropKind.CRAYON_RAIL, PropKind.PAINT_PALETTE -> {
                drawLitMeshRotated(
                    unitCube, prop.x, prop.y + prop.sy * 0.5f, prop.z,
                    0f, yawDeg, 0f,
                    prop.sx, prop.sy, prop.sz,
                    c1[0], c1[1], c1[2], 1f
                )
            }
        }
    }

    private fun drawGhostKart3D(ghost: RenderGhostSnapshot) {
        val yawDeg = Math.toDegrees(ghost.headingRad.toDouble()).toFloat()
        val bodyRgb = Meshes.hexToRgb(ghost.colorHex)
        val drmModel = kartDrmModels[ghost.bodyType]
        if (drmModel != null) {
            drawDrmModelSimple(
                model = drmModel,
                x = ghost.x, y = ghost.y, z = ghost.z,
                yawDeg = yawDeg,
                scale = 1.32f,
                paintRgb = bodyRgb,
                alpha = 0.42f
            )
        } else {
            drawLitMeshRotated(
                unitCube,
                x = ghost.x, y = ghost.y + 0.4f, z = ghost.z,
                rxDeg = 0f, ryDeg = yawDeg, rzDeg = 0f,
                sx = 1.42f, sy = 0.45f, sz = 2.20f,
                r = bodyRgb[0], g = bodyRgb[1], b = bodyRgb[2], a = 0.42f
            )
        }
    }

    private fun drawKart3D(kart: RenderKartSnapshot, forceAlpha: Float, isBattleMode: Boolean) {
        val totalYawRad = kart.headingRad + kart.visualYawOffsetRad
        val yawDeg = Math.toDegrees(totalYawRad.toDouble()).toFloat()
        val rollDeg = Math.toDegrees(kart.visualRollRad.toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(kart.visualPitchRad.toDouble()).toFloat()
        val activeColorHex = if (kart.paintTintHex != 0L) kart.paintTintHex else kart.colorHex
        val bodyRgb = Meshes.hexToRgb(activeColorHex)

        // 1. Fake Blob Shadow under Kart
        drawLitMeshRotated(
            unitCube,
            x = kart.x, y = kart.y.coerceAtLeast(0f) + 0.04f, z = kart.z,
            rxDeg = 0f, ryDeg = yawDeg, rzDeg = 0f,
            sx = 1.65f, sy = 0.02f, sz = 2.35f,
            r = 0.04f, g = 0.05f, b = 0.08f, a = 0.55f * forceAlpha
        )

        val drmModel = kartDrmModels[kart.bodyType]
        if (drmModel != null) {
            val steerDeg = Math.toDegrees(kart.frontWheelSteerRad.toDouble()).toFloat()
            val spinDeg = Math.toDegrees(kart.wheelSpinRad.toDouble()).toFloat()
            val kartScale = 1.32f

            for (part in drmModel.parts) {
                Matrix.setIdentityM(partMatrix, 0)
                Matrix.translateM(partMatrix, 0, kart.x, kart.y, kart.z)
                Matrix.rotateM(partMatrix, 0, yawDeg, 0f, 1f, 0f)
                if (pitchDeg != 0f) Matrix.rotateM(partMatrix, 0, pitchDeg, 1f, 0f, 0f)
                if (rollDeg != 0f) Matrix.rotateM(partMatrix, 0, rollDeg, 0f, 0f, 1f)
                Matrix.scaleM(partMatrix, 0, kartScale, kartScale, kartScale)
                Matrix.translateM(partMatrix, 0, part.pivotX, part.pivotY, part.pivotZ)

                if (part.isFrontSteered && steerDeg != 0f) {
                    Matrix.rotateM(partMatrix, 0, steerDeg, 0f, 1f, 0f)
                }
                if (part.isWheel && spinDeg != 0f) {
                    Matrix.rotateM(partMatrix, 0, spinDeg, 1f, 0f, 0f)
                }

                renderDrmPartBuffer(part, partMatrix, bodyRgb[0], bodyRgb[1], bodyRgb[2], forceAlpha)
            }
        } else {
            // Procedural Fallback if DRM asset is missing
            fun drawLocalPart(lx: Float, ly: Float, lz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float, mesh: GlMesh = unitCube) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, kart.x, kart.y, kart.z)
                Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
                if (pitchDeg != 0f) Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
                if (rollDeg != 0f) Matrix.rotateM(modelMatrix, 0, rollDeg, 0f, 0f, 1f)
                Matrix.translateM(modelMatrix, 0, lx, ly, lz)
                Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
                renderLitMeshBuffer(mesh, r, g, b, forceAlpha)
            }

            drawLocalPart(0f, 0.40f, 0f, 1.42f, 0.45f, 2.20f, bodyRgb[0], bodyRgb[1], bodyRgb[2])
            drawLocalPart(0f, 0.32f, 1.15f, 1.05f, 0.28f, 0.50f, bodyRgb[0] * 0.9f, bodyRgb[1] * 0.9f, bodyRgb[2] * 0.9f)
            drawLocalPart(0f, 0.90f, -0.95f, 1.50f, 0.12f, 0.40f, 0.98f, 0.80f, 0.08f)

            val wheelCoords = arrayOf(
                floatArrayOf(-0.85f, 0.35f, 0.74f),
                floatArrayOf(0.85f, 0.35f, 0.74f),
                floatArrayOf(-0.85f, 0.35f, -0.74f),
                floatArrayOf(0.85f, 0.35f, -0.74f)
            )
            for (wp in wheelCoords) {
                drawLocalPart(wp[0], wp[1], wp[2], 0.34f, 0.36f, 0.36f, 0.12f, 0.14f, 0.18f, wheelCylinder)
            }
        }

        // Driver Helmet & Visor
        fun drawKartAccessory(lx: Float, ly: Float, lz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float, mesh: GlMesh) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, kart.x, kart.y, kart.z)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            if (pitchDeg != 0f) Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
            if (rollDeg != 0f) Matrix.rotateM(modelMatrix, 0, rollDeg, 0f, 0f, 1f)
            Matrix.translateM(modelMatrix, 0, lx, ly, lz)
            Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
            renderLitMeshBuffer(mesh, r, g, b, forceAlpha)
        }

        drawKartAccessory(0f, 1.08f, -0.08f, 0.44f, 0.44f, 0.44f, bodyRgb[0], bodyRgb[1], bodyRgb[2], unitSphere)
        drawKartAccessory(0f, 1.10f, 0.28f, 0.48f, 0.16f, 0.18f, 0.15f, 0.75f, 0.98f, unitCube)

        // Held Shield Eraser or Orbiting Triple Tacks
        if (kart.shieldEraserHeld) {
            drawKartAccessory(0f, 0.42f, -1.75f, 1.2f, 0.5f, 0.7f, 0.96f, 0.35f, 0.55f, unitCube)
        }
        if (kart.orbitingTacksCount > 0) {
            for (i in 0 until kart.orbitingTacksCount) {
                val ang = (System.currentTimeMillis() * 0.005 + i * 2.09).toFloat()
                val tx = cos(ang) * 1.65f
                val tz = sin(ang) * 1.65f
                drawKartAccessory(tx, 0.45f, tz, 0.35f, 0.35f, 0.35f, 0.98f, 0.82f, 0.12f, unitSphere)
            }
        }

        // Battle Mode Balloons floating above rear bumper
        if (isBattleMode && kart.balloonsRemaining > 0) {
            for (b in 0 until kart.balloonsRemaining) {
                val bx = (b - 1) * 0.55f
                drawKartAccessory(bx, 1.65f, -1.1f, 0.48f, 0.58f, 0.48f, bodyRgb[0], bodyRgb[1], bodyRgb[2], unitSphere)
            }
        }
    }

    private fun drawDrmModelSimple(
        model: DrmModel,
        x: Float, y: Float, z: Float,
        yawDeg: Float,
        scale: Float,
        paintRgb: FloatArray,
        alpha: Float
    ) {
        for (part in model.parts) {
            Matrix.setIdentityM(partMatrix, 0)
            Matrix.translateM(partMatrix, 0, x, y, z)
            if (yawDeg != 0f) Matrix.rotateM(partMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(partMatrix, 0, scale, scale, scale)
            Matrix.translateM(partMatrix, 0, part.pivotX, part.pivotY, part.pivotZ)
            renderDrmPartBuffer(part, partMatrix, paintRgb[0], paintRgb[1], paintRgb[2], alpha)
        }
    }

    private fun renderDrmPartBuffer(
        part: DrmPartMesh,
        matrix: FloatArray,
        paintR: Float,
        paintG: Float,
        paintB: Float,
        alpha: Float
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, matrix, 0)
        GLES20.glUniformMatrix4fv(litMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(litModelLoc, 1, false, matrix, 0)
        GLES20.glUniform3f(litPaintColorLoc, paintR, paintG, paintB)
        GLES20.glUniform4f(litTintLoc, 1f, 1f, 1f, alpha)

        val buf = part.buffer
        val stride = DrmModelLoader.DRM_STRIDE_BYTES

        buf.position(0)
        GLES20.glEnableVertexAttribArray(litPosLoc)
        GLES20.glVertexAttribPointer(litPosLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(litNormLoc)
        GLES20.glVertexAttribPointer(litNormLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(6)
        GLES20.glEnableVertexAttribArray(litVertColorLoc)
        GLES20.glVertexAttribPointer(litVertColorLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        if (litPaintMaskLoc >= 0) {
            buf.position(9)
            GLES20.glEnableVertexAttribArray(litPaintMaskLoc)
            GLES20.glVertexAttribPointer(litPaintMaskLoc, 1, GLES20.GL_FLOAT, false, stride, buf)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, part.vertexCount)

        if (litPaintMaskLoc >= 0) {
            GLES20.glDisableVertexAttribArray(litPaintMaskLoc)
            GLES20.glVertexAttrib1f(litPaintMaskLoc, 0f)
        }
    }

    private fun drawLitMesh(
        mesh: GlMesh,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        renderLitMeshBuffer(mesh, r, g, b, a)
    }

    private fun drawLitMeshRotated(
        mesh: GlMesh,
        x: Float, y: Float, z: Float,
        rxDeg: Float, ryDeg: Float, rzDeg: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (ryDeg != 0f) Matrix.rotateM(modelMatrix, 0, ryDeg, 0f, 1f, 0f)
        if (rxDeg != 0f) Matrix.rotateM(modelMatrix, 0, rxDeg, 1f, 0f, 0f)
        if (rzDeg != 0f) Matrix.rotateM(modelMatrix, 0, rzDeg, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        renderLitMeshBuffer(mesh, r, g, b, a)
    }

    private fun renderLitMeshBuffer(mesh: GlMesh, r: Float, g: Float, b: Float, a: Float) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(litMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(litModelLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform4f(litTintLoc, r, g, b, a)

        val buf = mesh.buffer
        val stride = Meshes.LIT_STRIDE_BYTES

        buf.position(0)
        GLES20.glEnableVertexAttribArray(litPosLoc)
        GLES20.glVertexAttribPointer(litPosLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(litNormLoc)
        GLES20.glVertexAttribPointer(litNormLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(6)
        GLES20.glEnableVertexAttribArray(litVertColorLoc)
        GLES20.glVertexAttribPointer(litVertColorLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
    }
}
