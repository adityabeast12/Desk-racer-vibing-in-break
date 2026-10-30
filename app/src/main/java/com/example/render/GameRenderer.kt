package com.example.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.GameConstants
import com.example.game.KartBodyType
import com.example.game.ProjectileKind
import com.example.game.PropKind
import com.example.game.RacePhase
import com.example.game.RenderKartSnapshot
import com.example.game.TrackProp
import com.example.game.Tracks
import com.example.game.WorldRenderSnapshot
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class GameRenderer(
    private val snapshotRef: AtomicReference<WorldRenderSnapshot>
) : GLSurfaceView.Renderer {

    @Volatile
    var allowHighParticles: Boolean = true

    private val camera = Camera()
    private val particles = Particles(maxParticles = 180)

    private var litProgram = 0
    private var litPosLoc = 0
    private var litNormLoc = 0
    private var litVertColorLoc = 0
    private var litMvpLoc = 0
    private var litModelLoc = 0
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
    private val mvpMatrix = FloatArray(16)

    private lateinit var unitCube: GlMesh
    private lateinit var rainbowCube: GlMesh
    private lateinit var unitSphere: GlMesh
    private lateinit var wheelCylinder: GlMesh
    private val trackMeshes = HashMap<Int, GlMesh>()

    private var viewportWidth = 1280
    private var viewportHeight = 720

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        litProgram = Shaders.compileProgram(Shaders.LIT_VERTEX_SHADER, Shaders.LIT_FRAGMENT_SHADER)
        litPosLoc = GLES20.glGetAttribLocation(litProgram, "aPosition")
        litNormLoc = GLES20.glGetAttribLocation(litProgram, "aNormal")
        litVertColorLoc = GLES20.glGetAttribLocation(litProgram, "aVertColor")
        litMvpLoc = GLES20.glGetUniformLocation(litProgram, "uMvpMatrix")
        litModelLoc = GLES20.glGetUniformLocation(litProgram, "uModelMatrix")
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

        trackMeshes.clear()
        for (t in Tracks.all) {
            trackMeshes[t.id] = Meshes.buildTrackRoadMesh(t)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val snap = snapshotRef.get() ?: return
        val track = Tracks.byId(snap.trackId)
        val fogRgb = Meshes.hexToRgb(track.fogHex)

        GLES20.glClearColor(fogRgb[0], fogRgb[1], fogRgb[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (litProgram == 0) return

        val localKart = snap.karts.firstOrNull { it.slotIndex == snap.localSlotIndex }
            ?: snap.karts.firstOrNull()
        val isShowroom = snap.phase == RacePhase.SHOWROOM_MENU || snap.phase == RacePhase.RESULTS

        camera.updateChase(
            dt = GameConstants.SIM_DT,
            targetKart = localKart,
            isShowroom = isShowroom,
            showroomAngleRad = snap.showroomCameraAngleRad,
            landingShakeSignal = snap.landingShakeSignal
        )

        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, camera.currentFovDeg, aspect, 0.5f, 220f)
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

        // 1. Draw Desk Tabletop & Outer Rim Rails
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

        // 2. Draw Spline Track Ribbon (Road, Kerbs, Dashed Line, Checkered Start)
        val roadMesh = trackMeshes[track.id]
        if (roadMesh != null) {
            Matrix.setIdentityM(modelMatrix, 0)
            renderLitMeshBuffer(roadMesh, 1f, 1f, 1f, 1f)
        }

        // Start/Finish Overhead Gantry Banner
        val s0 = track.samples.first()
        val sYawDeg = Math.toDegrees(s0.headingRad.toDouble()).toFloat()
        val halfW = track.roadWidth * 0.5f + 1.1f
        drawLitMesh(
            unitCube,
            x = s0.pos.x - s0.rightX * halfW, y = 2.8f, z = s0.pos.z - s0.rightZ * halfW,
            sx = 0.65f, sy = 5.6f, sz = 0.65f,
            r = 1.0f, g = 0.16f, b = 0.37f, a = 1f
        )
        drawLitMesh(
            unitCube,
            x = s0.pos.x + s0.rightX * halfW, y = 2.8f, z = s0.pos.z + s0.rightZ * halfW,
            sx = 0.65f, sy = 5.6f, sz = 0.65f,
            r = 1.0f, g = 0.16f, b = 0.37f, a = 1f
        )
        drawLitMeshRotated(
            unitCube,
            x = s0.pos.x, y = 5.3f, z = s0.pos.z,
            rxDeg = 0f, ryDeg = sYawDeg, rzDeg = 0f,
            sx = halfW * 2.1f, sy = 0.85f, sz = 0.6f,
            r = 0.98f, g = 0.80f, b = 0.08f, a = 1f
        )

        // 3. Draw Track Props (Books, Mug, Pencils, Ruler Ramp, Sticky Notes, Keyboard, Kettle, Oranges)
        for (prop in track.props) {
            drawTrackProp(prop)
        }

        // 4. Draw Floating, Spinning Rainbow Item Boxes
        for (box in snap.itemBoxes) {
            if (!box.active) continue
            val deg = Math.toDegrees(box.rotationRad.toDouble()).toFloat()
            drawLitMeshRotated(
                rainbowCube,
                x = box.x, y = box.y + sin(box.rotationRad * 2f) * 0.18f, z = box.z,
                rxDeg = deg * 0.6f, ryDeg = deg, rzDeg = 25f,
                sx = 1.15f, sy = 1.15f, sz = 1.15f,
                r = 1f, g = 1f, b = 1f, a = 0.88f
            )
        }

        // 5. Draw Karts (with blob shadow, body type, wheels, driver helmet, orbiting tacks / shield)
        for (kart in snap.karts) {
            if (!kart.isInvulnerableFlashing) {
                drawKart3D(kart)
            }
        }

        // 6. Draw Active Projectiles (Marbles, Erasers, Tacks, Paper Planes)
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

        // 7. Update & Draw Pooled Unlit Particles (Sparks, Flames, Dust)
        particles.emitForKarts(GameConstants.SIM_DT, snap.karts, allowHighParticles)
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

    private fun drawTrackProp(prop: TrackProp) {
        val c1 = Meshes.hexToRgb(prop.colorHex)
        val c2 = Meshes.hexToRgb(prop.secondaryHex)
        val yawDeg = Math.toDegrees(prop.yawRad.toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(prop.pitchRad.toDouble()).toFloat()

        when (prop.kind) {
            PropKind.BOOK -> {
                // Hardcover textbook with white page block
                drawLitMeshRotated(unitCube, prop.x, prop.y + prop.sy * 0.5f, prop.z, 0f, yawDeg, 0f, prop.sx - 0.4f, prop.sy - 0.3f, prop.sz - 0.4f, 0.95f, 0.96f, 0.98f, 1f)
                drawLitMeshRotated(unitCube, prop.x, prop.y + prop.sy - 0.12f, prop.z, 0f, yawDeg, 0f, prop.sx, 0.25f, prop.sz, c1[0], c1[1], c1[2], 1f)
                drawLitMeshRotated(unitCube, prop.x, prop.y + 0.12f, prop.z, 0f, yawDeg, 0f, prop.sx, 0.25f, prop.sz, c2[0], c2[1], c2[2], 1f)
            }
            PropKind.MUG_ROUNDABOUT, PropKind.KETTLE_PILLAR -> {
                drawLitMesh(unitSphere, prop.x, prop.y + prop.sy * 0.45f, prop.z, prop.sx * 0.5f, prop.sy * 0.5f, prop.sz * 0.5f, c1[0], c1[1], c1[2], 1f)
                drawLitMesh(unitCube, prop.x + prop.sx * 0.52f, prop.y + prop.sy * 0.45f, prop.z, 1.2f, prop.sy * 0.5f, 0.8f, c2[0], c2[1], c2[2], 1f)
            }
            PropKind.ORANGE_BUMPER -> {
                drawLitMesh(unitSphere, prop.x, prop.y + prop.sy * 0.45f, prop.z, prop.sx * 0.5f, prop.sy * 0.5f, prop.sz * 0.5f, c1[0], c1[1], c1[2], 1f)
            }
            PropKind.RULER_RAMP, PropKind.LAPTOP_BRIDGE, PropKind.CUTTING_BOARD -> {
                drawLitMeshRotated(
                    unitCube, prop.x, prop.y + prop.sy * 0.35f, prop.z,
                    pitchDeg, yawDeg, 0f,
                    prop.sx, prop.sy * 0.3f, prop.sz,
                    c1[0], c1[1], c1[2], 1f
                )
            }
            PropKind.PENCIL_RAIL, PropKind.STICKY_BOOST_PAD, PropKind.KEYBOARD_BUMP -> {
                drawLitMeshRotated(
                    unitCube, prop.x, prop.y + prop.sy * 0.5f, prop.z,
                    0f, yawDeg, 0f,
                    prop.sx, prop.sy, prop.sz,
                    c1[0], c1[1], c1[2], 1f
                )
            }
        }
    }

    private fun drawKart3D(kart: RenderKartSnapshot) {
        val totalYawRad = kart.headingRad + kart.visualYawOffsetRad
        val yawDeg = Math.toDegrees(totalYawRad.toDouble()).toFloat()
        val rollDeg = Math.toDegrees(kart.visualRollRad.toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(kart.visualPitchRad.toDouble()).toFloat()
        val bodyRgb = Meshes.hexToRgb(kart.colorHex)

        // 1. Fake Blob Shadow under Kart
        drawLitMeshRotated(
            unitCube,
            x = kart.x, y = 0.04f, z = kart.z,
            rxDeg = 0f, ryDeg = yawDeg, rzDeg = 0f,
            sx = 1.65f, sy = 0.02f, sz = 2.35f,
            r = 0.04f, g = 0.05f, b = 0.08f, a = 0.58f
        )

        fun drawLocalPart(lx: Float, ly: Float, lz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float, mesh: GlMesh = unitCube) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, kart.x, kart.y, kart.z)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            if (pitchDeg != 0f) Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
            if (rollDeg != 0f) Matrix.rotateM(modelMatrix, 0, rollDeg, 0f, 0f, 1f)
            Matrix.translateM(modelMatrix, 0, lx, ly, lz)
            Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
            renderLitMeshBuffer(mesh, r, g, b, 1f)
        }

        // 2. Kart Body Silhouette (Classic, Pencil Rocket, or Eraser Tank)
        when (kart.bodyType) {
            KartBodyType.CLASSIC -> {
                drawLocalPart(0f, 0.40f, 0f, 1.42f, 0.45f, 2.20f, bodyRgb[0], bodyRgb[1], bodyRgb[2])
                drawLocalPart(0f, 0.32f, 1.15f, 1.05f, 0.28f, 0.50f, bodyRgb[0] * 0.9f, bodyRgb[1] * 0.9f, bodyRgb[2] * 0.9f)
                drawLocalPart(0f, 0.90f, -0.95f, 1.50f, 0.12f, 0.40f, 0.98f, 0.80f, 0.08f)
            }
            KartBodyType.PENCIL_ROCKET -> {
                // Sleek yellow/custom pencil fuselage + pink eraser tail + graphite tip nose
                drawLocalPart(0f, 0.44f, 0f, 1.05f, 0.52f, 2.55f, bodyRgb[0], bodyRgb[1], bodyRgb[2])
                drawLocalPart(0f, 0.44f, 1.38f, 0.65f, 0.35f, 0.45f, 0.18f, 0.20f, 0.24f)
                drawLocalPart(0f, 0.46f, -1.28f, 1.10f, 0.54f, 0.45f, 0.96f, 0.42f, 0.58f)
            }
            KartBodyType.ERASER_TANK -> {
                // Wide heavy dual-tone eraser block chassis
                drawLocalPart(0f, 0.46f, 0.2f, 1.68f, 0.56f, 1.85f, bodyRgb[0], bodyRgb[1], bodyRgb[2])
                drawLocalPart(0f, 0.46f, -0.85f, 1.68f, 0.56f, 0.85f, 0.22f, 0.62f, 0.96f)
            }
        }

        // 3. Driver Helmet & Visor
        drawLocalPart(0f, 0.95f, -0.05f, 0.46f, 0.46f, 0.46f, 0.99f, 0.88f, 0.28f, unitSphere)
        drawLocalPart(0f, 0.96f, 0.35f, 0.52f, 0.18f, 0.20f, 0.15f, 0.75f, 0.98f, unitCube)

        // 4. Four 3D Cylinder Wheels
        val wheelCoords = arrayOf(
            floatArrayOf(-0.85f, 0.35f, 0.74f),
            floatArrayOf(0.85f, 0.35f, 0.74f),
            floatArrayOf(-0.85f, 0.35f, -0.74f),
            floatArrayOf(0.85f, 0.35f, -0.74f)
        )
        for (wp in wheelCoords) {
            drawLocalPart(wp[0], wp[1], wp[2], 0.34f, 0.36f, 0.36f, 0.12f, 0.14f, 0.18f, wheelCylinder)
        }

        // 5. Held Shield Eraser or Orbiting Triple Tacks
        if (kart.shieldEraserHeld) {
            drawLocalPart(0f, 0.42f, -1.65f, 1.2f, 0.5f, 0.7f, 0.96f, 0.35f, 0.55f, unitCube)
        }
        if (kart.orbitingTacksCount > 0) {
            for (i in 0 until kart.orbitingTacksCount) {
                val ang = (System.currentTimeMillis() * 0.005 + i * 2.09).toFloat()
                val tx = cos(ang) * 1.65f
                val tz = sin(ang) * 1.65f
                drawLocalPart(tx, 0.45f, tz, 0.35f, 0.35f, 0.35f, 0.98f, 0.82f, 0.12f, unitSphere)
            }
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
