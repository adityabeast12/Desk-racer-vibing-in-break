package com.example.game

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class DeskRacerGlRenderer : GLSurfaceView.Renderer {

    @Volatile
    var currentState: DeskRacerUiState = DeskRacerUiState()

    private var programId = 0
    private var aPositionLoc = 0
    private var aNormalLoc = 0
    private var uColorLoc = 0
    private var uMvpMatrixLoc = 0
    private var uModelMatrixLoc = 0
    private var uLightDirLoc = 0
    private var uFogColorLoc = 0
    private var uCameraPosLoc = 0

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private lateinit var cubeBuffer: FloatBuffer
    private var cubeVertexCount = 0

    private lateinit var octahedronBuffer: FloatBuffer
    private var octahedronVertexCount = 0

    private lateinit var sphereBuffer: FloatBuffer
    private var sphereVertexCount = 0

    private lateinit var cylinderBuffer: FloatBuffer
    private var cylinderVertexCount = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.08f, 0.12f, 0.20f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        programId = createShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(programId, "aNormal")
        uColorLoc = GLES20.glGetUniformLocation(programId, "uColor")
        uMvpMatrixLoc = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrixLoc = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uLightDirLoc = GLES20.glGetUniformLocation(programId, "uLightDir")
        uFogColorLoc = GLES20.glGetUniformLocation(programId, "uFogColor")
        uCameraPosLoc = GLES20.glGetUniformLocation(programId, "uCameraPos")

        val cubeData = buildUnitCube()
        cubeVertexCount = cubeData.size / 6
        cubeBuffer = createFloatBuffer(cubeData)

        val octaData = buildUnitOctahedron()
        octahedronVertexCount = octaData.size / 6
        octahedronBuffer = createFloatBuffer(octaData)

        val sphereData = buildLowPolySphere(12, 16)
        sphereVertexCount = sphereData.size / 6
        sphereBuffer = createFloatBuffer(sphereData)

        val cylData = buildHorizontalWheelCylinder(14)
        cylinderVertexCount = cylData.size / 6
        cylinderBuffer = createFloatBuffer(cylData)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        // Wider 74-degree vertical FOV so Portrait screens see wide across the wooden desk
        Matrix.perspectiveM(projMatrix, 0, 74f, aspect, 0.5f, 180f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val state = currentState
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (programId == 0) return
        GLES20.glUseProgram(programId)

        Matrix.setLookAtM(
            viewMatrix, 0,
            state.cameraX, state.cameraY, state.cameraZ,
            state.cameraLookX, state.cameraLookY, state.cameraLookZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES20.glUniform3f(uLightDirLoc, 0.42f, 0.84f, 0.36f)
        GLES20.glUniform3f(uFogColorLoc, 0.08f, 0.12f, 0.20f)
        GLES20.glUniform3f(uCameraPosLoc, state.cameraX, state.cameraY, state.cameraZ)

        // 1. Draw 80x2x80 Warm Wooden Desk Base & Subtle Plank Strips
        drawBox(
            x = 0f, y = -1.0f, z = 0f,
            sx = 80f, sy = 2.0f, sz = 80f,
            r = 0.545f, g = 0.353f, b = 0.169f, a = 1f
        )
        // Outer Desk Rim / Bezel
        drawBox(0f, 0.35f, -40.5f, 82f, 0.7f, 1.0f, 0.36f, 0.22f, 0.10f, 1f)
        drawBox(0f, 0.35f, 40.5f, 82f, 0.7f, 1.0f, 0.36f, 0.22f, 0.10f, 1f)
        drawBox(-40.5f, 0.35f, 0f, 1.0f, 0.7f, 82f, 0.36f, 0.22f, 0.10f, 1f)
        drawBox(40.5f, 0.35f, 0f, 1.0f, 0.7f, 82f, 0.36f, 0.22f, 0.10f, 1f)

        // Checkered Start/Finish Line & Desk Props
        drawStartFinishLine()
        drawDeskPropsAndStationery()

        // 2. Draw 3 Textbook Obstacles with White Page Blocks & Colored Covers
        for (book in state.books) {
            drawTextbook3D(book)
        }

        // 3. Draw Yellow Ruler Jump Ramp at (-8, 0.9, -12) with 0.22 rad incline
        drawRulerRamp3D(state.rulerRamp)

        // 4. Draw 6 Rotating Cyan Octahedron Item Boxes
        for (box in state.itemBoxes) {
            if (box.visible) {
                drawOctahedronItemBox(box)
            }
        }

        // 5. Draw Karts (Local Kart, AI Rival Kart, and Remote Hotspot Peers)
        val localTilt = if (state.controls.drift) {
            when {
                state.controls.left -> 0.25f
                state.controls.right -> -0.25f
                else -> 0f
            }
        } else 0f

        drawKart3D(
            kart = state.localKart,
            driftYawOffset = localTilt,
            isUltraSpark = state.localKart.driftCharge > 35,
            isCharging = state.localKart.driftCharge in 5..35
        )

        if (state.aiRivalEnabled) {
            drawKart3D(
                kart = state.aiRivalKart,
                driftYawOffset = 0f,
                isUltraSpark = false,
                isCharging = false
            )
        }

        for ((_, peerKart) in state.remoteKarts) {
            drawKart3D(
                kart = peerKart,
                driftYawOffset = 0f,
                isUltraSpark = false,
                isCharging = false
            )
        }

        // 6. Draw Bouncy Marbles & Drift Spark Particles
        for (marble in state.marbles) {
            val c = hexToRgb(marble.colorHex)
            drawSphere(
                x = marble.x,
                y = marble.y,
                z = marble.z,
                radius = 0.58f,
                r = c[0], g = c[1], b = c[2], a = 1f
            )
        }

        for (spark in state.sparks) {
            val c = hexToRgb(spark.colorHex)
            drawBox(
                x = spark.x,
                y = spark.y,
                z = spark.z,
                sx = 0.24f, sy = 0.24f, sz = 0.24f,
                r = c[0], g = c[1], b = c[2], a = 0.95f
            )
        }
    }

    private fun drawStartFinishLine() {
        // Checkered Start/Finish Gate at z = 18, x from -10 to +10
        for (i in -5..4) {
            val x = i * 2.0f + 1.0f
            val isWhite = i % 2 == 0
            val c1 = if (isWhite) 0.96f else 0.10f
            val c2 = if (!isWhite) 0.96f else 0.10f
            drawBox(x, 0.02f, 17.5f, 1.95f, 0.04f, 1.2f, c1, c1, c1, 1f)
            drawBox(x, 0.02f, 18.7f, 1.95f, 0.04f, 1.2f, c2, c2, c2, 1f)
        }
        // Start/Finish Overhead Arch Pillars (Red & Cyan)
        drawBox(-10.5f, 2.5f, 18.1f, 0.6f, 5.0f, 0.6f, 1.0f, 0.16f, 0.37f, 1f)
        drawBox(10.5f, 2.5f, 18.1f, 0.6f, 5.0f, 0.6f, 1.0f, 0.16f, 0.37f, 1f)
        drawBox(0f, 4.8f, 18.1f, 21.6f, 0.7f, 0.5f, 0.12f, 0.16f, 0.24f, 1f)

        // Cyan & Amber Racing Circuit Tape Markers around the Desk Loop
        for (i in 0 until 24) {
            val ang = (i / 24.0) * Math.PI * 2.0
            val tx = (cos(ang) * 21.0).toFloat()
            val tz = (sin(ang) * 21.0).toFloat()
            val deg = Math.toDegrees(-ang).toFloat()
            drawBoxRotated(tx, 0.015f, tz, 0f, deg, 0f, 0.45f, 0.03f, 2.4f, 0.22f, 0.74f, 0.97f, 0.75f)
        }

        for (zLine in -32..32 step 8) {
            drawBox(0f, 0.01f, zLine.toFloat(), 78f, 0.02f, 0.14f, 0.42f, 0.26f, 0.11f, 1f)
        }
    }

    private fun drawDeskPropsAndStationery() {
        // Giant Coffee Mug at North-East corner (24, 0, -22)
        drawSphere(24f, 2.2f, -22f, 2.6f, 0.92f, 0.94f, 0.98f, 1f)
        drawBox(26.4f, 2.2f, -22f, 1.2f, 2.4f, 0.6f, 0.92f, 0.94f, 0.98f, 1f)

        // Giant Pencil Barrier along South Edge (0, 0.45, 33)
        drawBox(0f, 0.45f, 33f, 28f, 0.8f, 0.8f, 0.96f, 0.75f, 0.12f, 1f)
        drawBox(-14.8f, 0.45f, 33f, 1.8f, 0.82f, 0.82f, 0.94f, 0.45f, 0.58f, 1f)
    }

    private fun drawTextbook3D(book: BookObstacle) {
        val rgb = hexToRgb(book.colorHex)
        val spineRgb = hexToRgb(book.spineColorHex)

        drawBox(
            x = book.x, y = 0.12f, z = book.z,
            sx = book.width, sy = 0.24f, sz = book.depth,
            r = spineRgb[0], g = spineRgb[1], b = spineRgb[2], a = 1f
        )
        drawBox(
            x = book.x + 0.15f, y = book.height * 0.5f, z = book.z,
            sx = book.width - 0.4f, sy = book.height - 0.36f, sz = book.depth - 0.4f,
            r = 0.95f, g = 0.96f, b = 0.98f, a = 1f
        )
        drawBox(
            x = book.x, y = book.height - 0.12f, z = book.z,
            sx = book.width, sy = 0.24f, sz = book.depth,
            r = rgb[0], g = rgb[1], b = rgb[2], a = 1f
        )
        drawBox(
            x = book.x - book.width * 0.5f + 0.18f, y = book.height * 0.5f, z = book.z,
            sx = 0.36f, sy = book.height, sz = book.depth,
            r = spineRgb[0], g = spineRgb[1], b = spineRgb[2], a = 1f
        )
    }

    private fun drawRulerRamp3D(ramp: RulerRamp) {
        val pitchDeg = Math.toDegrees(ramp.pitchRad.toDouble()).toFloat()
        drawBoxRotated(
            x = ramp.x, y = ramp.y, z = ramp.z,
            rxDeg = pitchDeg, ryDeg = 0f, rzDeg = 0f,
            sx = ramp.width, sy = ramp.height, sz = ramp.depth,
            r = 0.98f, g = 0.80f, b = 0.08f, a = 1f
        )
        for (i in -4..4) {
            val localZ = i * 1.1f
            val worldZ = ramp.z + localZ * cos(ramp.pitchRad)
            val worldY = ramp.y - localZ * sin(ramp.pitchRad) + 0.18f
            drawBoxRotated(
                x = ramp.x - ramp.width * 0.32f,
                y = worldY,
                z = worldZ,
                rxDeg = pitchDeg, ryDeg = 0f, rzDeg = 0f,
                sx = 1.4f, sy = 0.06f, sz = 0.14f,
                r = 0.12f, g = 0.16f, b = 0.23f, a = 1f
            )
        }
    }

    private fun drawOctahedronItemBox(box: ItemBoxEntity) {
        val ryDeg = Math.toDegrees(box.rotY.toDouble()).toFloat()
        val rxDeg = Math.toDegrees(box.rotX.toDouble()).toFloat()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, box.x, box.y, box.z)
        Matrix.rotateM(modelMatrix, 0, ryDeg, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, rxDeg, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 1.05f, 1.05f, 1.05f)

        drawMesh(octahedronBuffer, octahedronVertexCount, 0.22f, 0.74f, 0.97f, 0.88f)
    }

    private fun drawKart3D(
        kart: KartState,
        driftYawOffset: Float,
        isUltraSpark: Boolean,
        isCharging: Boolean
    ) {
        val yawRad = kart.rot + driftYawOffset
        val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()
        val bodyRgb = hexToRgb(kart.colorHex)

        // 1. Drop Shadow on Desk
        drawBoxRotated(
            x = kart.x, y = 0.02f, z = kart.z,
            rxDeg = 0f, ryDeg = yawDeg, rzDeg = 0f,
            sx = 1.65f, sy = 0.02f, sz = 2.4f,
            r = 0.05f, g = 0.05f, b = 0.08f, a = 0.55f
        )

        fun drawPart(lx: Float, ly: Float, lz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, kart.x, kart.y, kart.z)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.translateM(modelMatrix, 0, lx, ly, lz)
            Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
            drawMesh(cubeBuffer, cubeVertexCount, r, g, b, 1f)
        }

        // 2. Main Kart Chassis (1.4 x 0.48 x 2.2 at y = 0.4)
        drawPart(0f, 0.40f, 0f, 1.42f, 0.46f, 2.20f, bodyRgb[0], bodyRgb[1], bodyRgb[2])
        // Front Nose Cone
        drawPart(0f, 0.32f, -1.15f, 1.10f, 0.28f, 0.50f, bodyRgb[0] * 0.9f, bodyRgb[1] * 0.9f, bodyRgb[2] * 0.9f)
        // Side Pods
        drawPart(-0.78f, 0.30f, 0f, 0.22f, 0.24f, 1.50f, 0.15f, 0.18f, 0.24f)
        drawPart(0.78f, 0.30f, 0f, 0.22f, 0.24f, 1.50f, 0.15f, 0.18f, 0.24f)
        // Rear Yellow Spoiler Wing
        drawPart(-0.45f, 0.72f, 0.92f, 0.12f, 0.35f, 0.18f, 0.15f, 0.18f, 0.24f)
        drawPart(0.45f, 0.72f, 0.92f, 0.12f, 0.35f, 0.18f, 0.15f, 0.18f, 0.24f)
        drawPart(0f, 0.92f, 0.98f, 1.55f, 0.12f, 0.42f, 0.98f, 0.80f, 0.08f)

        // Dual Twin Exhaust Pipes
        val exhaustR = if (isUltraSpark) 1.0f else if (isCharging) 0.22f else 0.35f
        val exhaustG = if (isUltraSpark) 0.45f else if (isCharging) 0.74f else 0.38f
        val exhaustB = if (isUltraSpark) 0.05f else if (isCharging) 0.98f else 0.42f
        drawPart(-0.35f, 0.52f, 1.18f, 0.22f, 0.22f, 0.35f, exhaustR, exhaustG, exhaustB)
        drawPart(0.35f, 0.52f, 1.18f, 0.22f, 0.22f, 0.35f, exhaustR, exhaustG, exhaustB)

        // 3. Driver Helmet (Sphere at (0, 0.94, -0.08)) & Cyan Visor
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, kart.x, kart.y, kart.z)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(modelMatrix, 0, 0f, 0.94f, -0.08f)
        Matrix.scaleM(modelMatrix, 0, 0.46f, 0.46f, 0.46f)
        drawMesh(sphereBuffer, sphereVertexCount, 0.99f, 0.88f, 0.28f, 1f)

        drawPart(0f, 0.96f, -0.44f, 0.56f, 0.20f, 0.22f, 0.22f, 0.74f, 0.97f)

        // 4. Four 3D Cylinder Wheels ([±0.84, 0.35, ±0.72])
        val wheelPositions = arrayOf(
            floatArrayOf(-0.84f, 0.35f, 0.72f),
            floatArrayOf(0.84f, 0.35f, 0.72f),
            floatArrayOf(-0.84f, 0.35f, -0.72f),
            floatArrayOf(0.84f, 0.35f, -0.72f)
        )
        for (wp in wheelPositions) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, kart.x, kart.y, kart.z)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.translateM(modelMatrix, 0, wp[0], wp[1], wp[2])
            Matrix.scaleM(modelMatrix, 0, 0.34f, 0.36f, 0.36f)
            drawMesh(cylinderBuffer, cylinderVertexCount, 0.11f, 0.13f, 0.16f, 1f)
        }
    }

    private fun drawBox(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        drawMesh(cubeBuffer, cubeVertexCount, r, g, b, a)
    }

    private fun drawBoxRotated(
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
        drawMesh(cubeBuffer, cubeVertexCount, r, g, b, a)
    }

    private fun drawSphere(
        x: Float, y: Float, z: Float,
        radius: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, radius, radius, radius)
        drawMesh(sphereBuffer, sphereVertexCount, r, g, b, a)
    }

    private fun drawMesh(buffer: FloatBuffer, vertexCount: Int, r: Float, g: Float, b: Float, a: Float) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform4f(uColorLoc, r, g, b, a)

        val stride = 6 * 4
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, stride, buffer)

        buffer.position(3)
        GLES20.glEnableVertexAttribArray(aNormalLoc)
        GLES20.glVertexAttribPointer(aNormalLoc, 3, GLES20.GL_FLOAT, false, stride, buffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    }

    private fun hexToRgb(hex: Long): FloatArray {
        val r = ((hex shr 16) and 0xFF) / 255f
        val g = ((hex shr 8) and 0xFF) / 255f
        val b = (hex and 0xFF) / 255f
        return floatArrayOf(r, g, b)
    }

    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    private fun buildUnitCube(): FloatArray {
        val out = ArrayList<Float>(36 * 6)
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
            }
        }
        addFace(-0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0f, 1f, 0f)
        addFace(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0f, -1f, 0f)
        addFace(-0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0f, 0f, 1f)
        addFace(0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0f, 0f, -1f)
        addFace(0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 1f, 0f, 0f)
        addFace(-0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -1f, 0f, 0f)
        return out.toFloatArray()
    }

    private fun buildUnitOctahedron(): FloatArray {
        val out = ArrayList<Float>(24 * 6)
        val top = floatArrayOf(0f, 1f, 0f)
        val bot = floatArrayOf(0f, -1f, 0f)
        val ring = arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 0f, -1f)
        )
        fun addTri(a: FloatArray, b: FloatArray, c: FloatArray) {
            val nx = (a[0] + b[0] + c[0]) / 3f
            val ny = (a[1] + b[1] + c[1]) / 3f
            val nz = (a[2] + b[2] + c[2]) / 3f
            for (v in arrayOf(a, b, c)) {
                out.add(v[0]); out.add(v[1]); out.add(v[2])
                out.add(nx); out.add(ny); out.add(nz)
            }
        }
        for (i in 0 until 4) {
            val p0 = ring[i]
            val p1 = ring[(i + 1) % 4]
            addTri(top, p1, p0)
            addTri(bot, p0, p1)
        }
        return out.toFloatArray()
    }

    private fun buildLowPolySphere(stacks: Int, slices: Int): FloatArray {
        val out = ArrayList<Float>()
        for (i in 0 until stacks) {
            val phi0 = Math.PI * (i.toDouble() / stacks - 0.5)
            val phi1 = Math.PI * ((i + 1).toDouble() / stacks - 0.5)
            val y0 = sin(phi0).toFloat(); val r0 = cos(phi0).toFloat()
            val y1 = sin(phi1).toFloat(); val r1 = cos(phi1).toFloat()

            for (j in 0 until slices) {
                val th0 = 2.0 * Math.PI * j / slices
                val th1 = 2.0 * Math.PI * (j + 1) / slices
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
                }
            }
        }
        return out.toFloatArray()
    }

    private fun buildHorizontalWheelCylinder(slices: Int): FloatArray {
        val out = ArrayList<Float>()
        for (i in 0 until slices) {
            val a0 = (2.0 * Math.PI * i / slices).toFloat()
            val a1 = (2.0 * Math.PI * (i + 1) / slices).toFloat()
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
            }
            val capR = arrayOf(
                floatArrayOf(0.5f, 0f, 0f, 1f, 0f, 0f),
                floatArrayOf(0.5f, y0, z0, 1f, 0f, 0f),
                floatArrayOf(0.5f, y1, z1, 1f, 0f, 0f),
                floatArrayOf(-0.5f, 0f, 0f, -1f, 0f, 0f),
                floatArrayOf(-0.5f, y1, z1, -1f, 0f, 0f),
                floatArrayOf(-0.5f, y0, z0, -1f, 0f, 0f)
            )
            for (v in capR) {
                for (c in v) out.add(c)
            }
        }
        return out.toFloatArray()
    }

    private fun createShaderProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        // Strict GLSL ES 1.00 (no mat3(mat4) constructor, which fails to compile on mobile GPUs!)
        private const val VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vWorldPos;
            varying vec3 vNormal;

            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vec3 worldNorm = (uModelMatrix * vec4(aNormal, 0.0)).xyz;
                vNormal = normalize(worldNorm);
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLightDir;
            uniform vec3 uFogColor;
            uniform vec3 uCameraPos;
            varying vec3 vWorldPos;
            varying vec3 vNormal;

            void main() {
                vec3 norm = normalize(vNormal);
                float diff = max(dot(norm, normalize(uLightDir)), 0.0);
                float ambient = 0.58;
                vec3 viewDir = normalize(uCameraPos - vWorldPos);
                float rim = pow(1.0 - max(dot(norm, viewDir), 0.0), 3.0) * 0.20;
                vec3 litColor = uColor.rgb * (ambient + diff * 0.58) + vec3(rim);

                if (abs(vWorldPos.y) < 0.03 && uColor.r > 0.45 && uColor.g < 0.42) {
                    float plank = sin(vWorldPos.x * 2.2) * 0.025 + sin(vWorldPos.z * 0.4) * 0.02;
                    litColor += vec3(plank);
                }

                float dist = length(uCameraPos - vWorldPos);
                float fogFactor = clamp((dist - 42.0) / 68.0, 0.0, 1.0);
                vec3 finalRgb = mix(litColor, uFogColor, fogFactor);
                gl_FragColor = vec4(finalRgb, uColor.a);
            }
        """
    }
}
