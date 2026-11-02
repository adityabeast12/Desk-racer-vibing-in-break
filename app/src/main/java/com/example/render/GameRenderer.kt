package com.example.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.DynamicHazardKind
import com.example.game.GameConstants
import com.example.game.GameMode
import com.example.game.ItemType
import com.example.game.KartBodyType
import com.example.game.ProjectileKind
import com.example.game.PropKind
import com.example.game.RacePhase
import com.example.game.RenderDynamicHazardSnapshot
import com.example.game.RenderGhostSnapshot
import com.example.game.RenderKartSnapshot
import com.example.game.SplineSample
import com.example.game.Track
import com.example.game.TrackDecal
import com.example.game.TrackJsonLoader
import com.example.game.TrackProp
import com.example.game.Tracks
import com.example.game.WorldRenderSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GameRenderer(
    private val snapshotRef: AtomicReference<WorldRenderSnapshot>,
    private val appContext: Context? = null
) : GLSurfaceView.Renderer {

    @Volatile
    var allowHighParticles: Boolean = true

    @Volatile
    var allowContactShadows: Boolean = true

    @Volatile
    var allowPostFx: Boolean = true

    /**
     * Part D2: 0 = Low (normal maps off), 1 = Medium (strength 0.8), 2 = High (strength 1.0)
     */
    @Volatile
    var qualityLevel: Int = 2

    /**
     * Part E1-E3: Garage stage mode & interactive turntable state
     */
    @Volatile
    var isGarageStageActive: Boolean = false

    @Volatile
    var garageTurntableYawRad: Float = 0.55f

    @Volatile
    var garageZoomFactor: Float = 1.0f

    /**
     * Part E5: Paint finish index: 0=Gloss, 1=Matte, 2=Metallic, 3=Pearl
     */
    @Volatile
    var garagePaintFinishIndex: Int = 0

    private val pendingUnloadTrackAssets = AtomicBoolean(false)

    val assetRepo = AssetRepository(appContext)
    private val camera = Camera()
    private val particles = Particles(maxParticles = 240)

    // Unified PBR-lite program
    private var litProgram = 0
    private var litPosLoc = 0
    private var litNormLoc = 0
    private var litTexCoordLoc = 0
    private var litVertColorLoc = 0
    private var litPaintMaskLoc = 0
    private var litTangentLoc = 0
    private var litMvpLoc = 0
    private var litModelLoc = 0
    private var litBaseColorTexLoc = 0
    private var litEnvEquirectTexLoc = 0
    private var litNormalTexLoc = 0
    private var litBaseColorFactorLoc = 0
    private var litPaintColorLoc = 0
    private var litTintLoc = 0
    private var litMaterialParamsLoc = 0
    private var litNormalParamsLoc = 0
    private var litSkyAmbientLoc = 0
    private var litGroundAmbientLoc = 0
    private var litKeyLightDirLoc = 0
    private var litKeyLightColorLoc = 0
    private var litPointLightPosLoc = 0
    private var litPointLightColorLoc = 0
    private var litFogColorLoc = 0
    private var litCamPosLoc = 0

    // Equirectangular backdrop dome program
    private var backdropProgram = 0
    private var backdropPosLoc = 0
    private var backdropTexCoordLoc = 0
    private var backdropMvpLoc = 0
    private var backdropTexLoc = 0
    private var backdropTintLoc = 0
    private var backdropIsSrgbLoc = 0

    // Unlit particle & contact AO program
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
    private lateinit var flatQuadXZ: GlMesh
    private lateinit var rainbowCube: GlMesh
    private lateinit var unitSphere: GlMesh
    private lateinit var wheelCylinder: GlMesh
    private lateinit var backdropSphere: GlMesh
    private lateinit var contactShadowDisc: GlMesh
    private val trackMeshes = HashMap<Int, TrackRenderMeshes>()

    // Part D3: Ring buffer of 2,000 drift skid mark quads fading over 20 seconds
    private val maxSkidMarks = 2000
    private val skidX = FloatArray(maxSkidMarks)
    private val skidY = FloatArray(maxSkidMarks)
    private val skidZ = FloatArray(maxSkidMarks)
    private val skidYawDeg = FloatArray(maxSkidMarks)
    private val skidBirthSec = FloatArray(maxSkidMarks) { -100f }
    private var skidWriteCursor = 0
    private var skidTotalCount = 0
    private val lastKartSkidX = FloatArray(GameConstants.MAX_RACERS)
    private val lastKartSkidZ = FloatArray(GameConstants.MAX_RACERS)

    // Pre-allocated index array for back-to-front decal sorting (zero frame allocation)
    private var sortedDecalIndices = IntArray(64)
    private var sortedDecalDistSq = FloatArray(64)

    private var viewportWidth = 1280
    private var viewportHeight = 720
    private var surfaceFrameCount = 0
    private var coreAssetsWarmed = false
    private var currentEnvIsSrgb = 1f

    init {
        if (appContext != null) {
            TrackJsonLoader.appContext = appContext.applicationContext
        }
    }

    fun requestUnloadRaceAssets() {
        pendingUnloadTrackAssets.set(true)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        assetRepo.onGlContextRecreated()

        litProgram = Shaders.compileProgram(Shaders.LIT_VERTEX_SHADER, Shaders.LIT_FRAGMENT_SHADER)
        litPosLoc = GLES20.glGetAttribLocation(litProgram, "aPosition")
        litNormLoc = GLES20.glGetAttribLocation(litProgram, "aNormal")
        litTexCoordLoc = GLES20.glGetAttribLocation(litProgram, "aTexCoord")
        litVertColorLoc = GLES20.glGetAttribLocation(litProgram, "aVertColor")
        litPaintMaskLoc = GLES20.glGetAttribLocation(litProgram, "aPaintMask")
        litTangentLoc = GLES20.glGetAttribLocation(litProgram, "aTangent")

        litMvpLoc = GLES20.glGetUniformLocation(litProgram, "uMvpMatrix")
        litModelLoc = GLES20.glGetUniformLocation(litProgram, "uModelMatrix")
        litBaseColorTexLoc = GLES20.glGetUniformLocation(litProgram, "uBaseColorTex")
        litEnvEquirectTexLoc = GLES20.glGetUniformLocation(litProgram, "uEnvEquirectTex")
        litNormalTexLoc = GLES20.glGetUniformLocation(litProgram, "uNormalTex")
        litBaseColorFactorLoc = GLES20.glGetUniformLocation(litProgram, "uBaseColorFactor")
        litPaintColorLoc = GLES20.glGetUniformLocation(litProgram, "uPaintColor")
        litTintLoc = GLES20.glGetUniformLocation(litProgram, "uTintColor")
        litMaterialParamsLoc = GLES20.glGetUniformLocation(litProgram, "uMaterialParams")
        litNormalParamsLoc = GLES20.glGetUniformLocation(litProgram, "uNormalParams")
        litSkyAmbientLoc = GLES20.glGetUniformLocation(litProgram, "uSkyAmbient")
        litGroundAmbientLoc = GLES20.glGetUniformLocation(litProgram, "uGroundAmbient")
        litKeyLightDirLoc = GLES20.glGetUniformLocation(litProgram, "uKeyLightDir")
        litKeyLightColorLoc = GLES20.glGetUniformLocation(litProgram, "uKeyLightColor")
        litPointLightPosLoc = GLES20.glGetUniformLocation(litProgram, "uPointLightPos")
        litPointLightColorLoc = GLES20.glGetUniformLocation(litProgram, "uPointLightColor")
        litFogColorLoc = GLES20.glGetUniformLocation(litProgram, "uFogColor")
        litCamPosLoc = GLES20.glGetUniformLocation(litProgram, "uCameraPos")

        backdropProgram = Shaders.compileProgram(Shaders.BACKDROP_VERTEX_SHADER, Shaders.BACKDROP_FRAGMENT_SHADER)
        backdropPosLoc = GLES20.glGetAttribLocation(backdropProgram, "aPosition")
        backdropTexCoordLoc = GLES20.glGetAttribLocation(backdropProgram, "aTexCoord")
        backdropMvpLoc = GLES20.glGetUniformLocation(backdropProgram, "uMvpMatrix")
        backdropTexLoc = GLES20.glGetUniformLocation(backdropProgram, "uBackdropTex")
        backdropTintLoc = GLES20.glGetUniformLocation(backdropProgram, "uTint")
        backdropIsSrgbLoc = GLES20.glGetUniformLocation(backdropProgram, "uIsHardwareSrgb")

        unlitProgram = Shaders.compileProgram(Shaders.UNLIT_VERTEX_SHADER, Shaders.UNLIT_FRAGMENT_SHADER)
        unlitPosLoc = GLES20.glGetAttribLocation(unlitProgram, "aPosition")
        unlitColorLoc = GLES20.glGetAttribLocation(unlitProgram, "aColor")
        unlitMvpLoc = GLES20.glGetUniformLocation(unlitProgram, "uMvpMatrix")
        unlitTintLoc = GLES20.glGetUniformLocation(unlitProgram, "uTintColor")

        unitCube = Meshes.buildUnitCube()
        flatQuadXZ = Meshes.buildFlatQuadXZ()
        rainbowCube = Meshes.buildRainbowItemCube()
        unitSphere = Meshes.buildSphere(10, 14)
        wheelCylinder = Meshes.buildWheelCylinder(14)
        backdropSphere = Meshes.buildBackdropSphere(radius = 900f, stacks = 18, slices = 28)
        contactShadowDisc = Meshes.buildContactShadowDisc(18)

        trackMeshes.clear()
        surfaceFrameCount = 0
        coreAssetsWarmed = false
        clearSkidMarks()
    }

    private fun clearSkidMarks() {
        skidWriteCursor = 0
        skidTotalCount = 0
        for (i in 0 until maxSkidMarks) {
            skidBirthSec[i] = -100f
        }
    }

    private fun warmCoreAssetsIfNeeded() {
        if (coreAssetsWarmed) return
        coreAssetsWarmed = true
        for (body in KartBodyType.entries) {
            if (body.isDrm2) {
                val m2 = assetRepo.getDrm2Model(body.drmAssetPath)
                assetRepo.preloadModelTextures(m2)
            } else {
                assetRepo.getDrm1Model(body.drmAssetPath)
            }
        }
        assetRepo.getDrm1Model("models/coin.drm")
        assetRepo.getDrm1Model("models/flag.drm")
        assetRepo.getDrm1Model("models/cloud.drm")

        val itemPaths = arrayOf(
            "models/items/battery.drm",
            "models/items/eraser.drm",
            "models/items/magnet.drm",
            "models/items/marble.drm",
            "models/items/marble_homing.drm",
            "models/items/paper_plane.drm",
            "models/items/star_sticker.drm",
            "models/items/thumbtack.drm"
        )
        for (p in itemPaths) {
            val m = assetRepo.getDrm2Model(p)
            assetRepo.preloadModelTextures(m)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        surfaceFrameCount++
        if (pendingUnloadTrackAssets.compareAndSet(true, false)) {
            assetRepo.unloadRaceTrackAssets()
            clearSkidMarks()
        }

        val snap = snapshotRef.get()
        if (snap == null) {
            GLES20.glClearColor(0.043f, 0.063f, 0.125f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            return
        }
        val track = Tracks.byId(snap.trackId)
        val fogRgb = Meshes.hexToRgb(track.fogHex)

        GLES20.glClearColor(fogRgb[0], fogRgb[1], fogRgb[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (litProgram == 0) return

        // Complete Frame 1 immediately so WindowManager SurfaceSyncGroup finishes in <10ms
        if (surfaceFrameCount == 1) {
            return
        }
        if (surfaceFrameCount >= 3 && !coreAssetsWarmed) {
            warmCoreAssetsIfNeeded()
        }

        val localKart = snap.karts.firstOrNull { it.slotIndex == snap.localSlotIndex }
            ?: snap.karts.firstOrNull()

        val garageActive = isGarageStageActive && snap.phase == RacePhase.SHOWROOM_MENU
        val effectiveShowroomAngle = if (garageActive) garageTurntableYawRad else snap.showroomCameraAngleRad

        camera.updateChase(
            dt = GameConstants.SIM_DT,
            targetKart = localKart,
            phase = snap.phase,
            track = track,
            flybyProgress01 = snap.flybyProgress,
            showroomAngleRad = effectiveShowroomAngle,
            landingShakeSignal = snap.landingShakeSignal,
            isGarageStage = garageActive,
            garageZoom = garageZoomFactor,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )

        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Camera.buildOffCenterPerspectiveMatrix(
            outProj = projMatrix,
            fovYDeg = camera.currentFovDeg,
            aspect = aspect,
            near = 0.5f,
            far = 1400f,
            lensShiftX = camera.currentLensShiftX
        )
        Matrix.setLookAtM(
            viewMatrix, 0,
            camera.posX, camera.posY, camera.posZ,
            camera.lookX, camera.lookY, camera.lookZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // 1. Draw Equirectangular Room Backdrop Sphere (depth write OFF)
        val backdropPath = if (garageActive) "textures/backdrops/room_studio.jpg" else track.backdropEquirect
        val backdropTexId = assetRepo.getTextureId(backdropPath, repeatWrap = false)
        currentEnvIsSrgb = if (assetRepo.isTextureHardwareSrgb(backdropTexId)) 1f else 0f
        drawBackdropDome(backdropTexId, track)

        // 2. Setup Unified PBR-Lite Shader & Lighting Preset Uniforms
        GLES20.glUseProgram(litProgram)
        val lp = if (garageActive) com.example.game.LightingPreset.SUNNY_STUDIO else track.lightingPreset
        GLES20.glUniform3f(litSkyAmbientLoc, lp.skyAmbientRgb[0], lp.skyAmbientRgb[1], lp.skyAmbientRgb[2])
        GLES20.glUniform3f(litGroundAmbientLoc, lp.groundAmbientRgb[0], lp.groundAmbientRgb[1], lp.groundAmbientRgb[2])
        GLES20.glUniform3f(litKeyLightDirLoc, lp.keyLightDir[0], lp.keyLightDir[1], lp.keyLightDir[2])
        GLES20.glUniform3f(litKeyLightColorLoc, lp.keyLightRgb[0], lp.keyLightRgb[1], lp.keyLightRgb[2])

        val lampProp = track.placedProps.firstOrNull { it.isEmissiveLamp }
        val ptX = if (garageActive && localKart != null) localKart.x - 4f else (lampProp?.x ?: -80f)
        val ptY = if (garageActive && localKart != null) localKart.y + 8f else ((lampProp?.y ?: 0f) + 32f)
        val ptZ = if (garageActive && localKart != null) localKart.z + 4f else (lampProp?.z ?: 0f)
        GLES20.glUniform3f(litPointLightPosLoc, ptX, ptY, ptZ)
        GLES20.glUniform3f(litPointLightColorLoc, lp.pointLightRgb[0], lp.pointLightRgb[1], lp.pointLightRgb[2])
        GLES20.glUniform3f(litFogColorLoc, fogRgb[0], fogRgb[1], fogRgb[2])
        GLES20.glUniform3f(litCamPosLoc, camera.posX, camera.posY, camera.posZ)

        // Bind environment equirectangular texture to unit 1, flat normal to unit 2
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backdropTexId)
        GLES20.glUniform1i(litEnvEquirectTexLoc, 1)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, assetRepo.getFlatNormalTexture())
        GLES20.glUniform1i(litNormalTexLoc, 2)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(litBaseColorTexLoc, 0)

        if (litPaintMaskLoc >= 0) {
            GLES20.glDisableVertexAttribArray(litPaintMaskLoc)
            GLES20.glVertexAttrib1f(litPaintMaskLoc, 0f)
        }
        if (litTangentLoc >= 0) {
            GLES20.glDisableVertexAttribArray(litTangentLoc)
            GLES20.glVertexAttrib3f(litTangentLoc, 1f, 0f, 0f)
        }

        // Part D2: Normal map strength based on quality setting (0.0 Low, 0.8 Medium, 1.0 High)
        val normalStrength = when {
            qualityLevel <= 0 -> 0.0f
            qualityLevel == 1 -> 0.8f
            else -> 1.0f
        }

        // 3. Draw Textured Desk Tabletop Slab, Road Ribbon, Kerbs, Finish Banner & Bridge Supports
        val tMeshes = trackMeshes.getOrPut(track.id) {
            Meshes.buildTrackMeshes(track)
        }
        val surfaceTexId = assetRepo.getTextureId(track.surfaceTexture, repeatWrap = true)
        val surfaceNormId = if (normalStrength > 0f) {
            assetRepo.getTextureId(track.surfaceNormal, repeatWrap = true, isNormalMap = true)
        } else {
            assetRepo.getFlatNormalTexture()
        }
        val roadTexId = assetRepo.getTextureId(track.roadTexture, repeatWrap = true)
        val roadNormId = if (normalStrength > 0f) {
            assetRepo.getTextureId(track.roadNormal, repeatWrap = true, isNormalMap = true)
        } else {
            assetRepo.getFlatNormalTexture()
        }
        val kerbTexId = assetRepo.getTextureId(track.kerbTexture, repeatWrap = true)
        val finishTexId = assetRepo.getTextureId(track.finishTexture, repeatWrap = true)
        val walnutTexId = assetRepo.getTextureId("textures/surfaces/wood_walnut.jpg", repeatWrap = true)

        Matrix.setIdentityM(modelMatrix, 0)
        // Desk surface with tangent = world +X (1, 0, 0) and surfaceNormal
        renderLitMeshBuffer(
            mesh = tMeshes.deskSlabMesh,
            texId = surfaceTexId,
            r = 1f, g = 1f, b = 1f, a = 1f,
            roughness = 0.62f,
            normalTexId = surfaceNormId,
            normalMapStrength = normalStrength
        )

        if (!garageActive) {
            // Road surface with spline tangent buffer and roadNormal
            renderLitMeshBuffer(
                mesh = tMeshes.roadRibbonMesh,
                texId = roadTexId,
                r = 1f, g = 1f, b = 1f, a = 1f,
                roughness = 0.72f,
                normalTexId = roadNormId,
                normalMapStrength = normalStrength
            )
            renderLitMeshBuffer(tMeshes.kerbRibbonMesh, kerbTexId, 1f, 1f, 1f, 1f, roughness = 0.50f)
            renderLitMeshBuffer(tMeshes.finishBannerMesh, finishTexId, 1f, 1f, 1f, 1f, roughness = 0.45f)
            if (tMeshes.bridgeSupportMesh.vertexCount > 0) {
                renderLitMeshBuffer(tMeshes.bridgeSupportMesh, walnutTexId, 1f, 1f, 1f, 1f, roughness = 0.58f)
            }
        }

        // Part D3: Track Decals, Start Grid Boxes, Boost Pad Chevrons, and Drift Skid Trails
        if (!garageActive) {
            drawTrackDecalsAndGridBoxes(track, snap)
        } else if (localKart != null) {
            // Part E3: Garage glowing turntable disc (`textures/ui/garage_turntable.png`)
            val turntableTexId = assetRepo.getTextureId("textures/ui/garage_turntable.png", repeatWrap = false)
            GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
            GLES20.glPolygonOffset(-1f, -2f)
            val spinDeg = Math.toDegrees((garageTurntableYawRad * 0.5f).toDouble()).toFloat()
            drawLitMeshRotated(
                mesh = flatQuadXZ,
                texId = turntableTexId,
                x = localKart.x, y = localKart.y + 0.02f, z = localKart.z,
                rxDeg = 0f, ryDeg = spinDeg, rzDeg = 0f,
                sx = 5.4f, sy = 1f, sz = 5.4f,
                r = 1f, g = 1f, b = 1f, a = 0.96f,
                metallic = 0.35f, roughness = 0.18f
            )
            GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        }

        // Part D4 & E3: In Main Menu showroom or Garage stage, render velvet_sofa & damask_chair in the background room
        if (snap.phase == RacePhase.SHOWROOM_MENU && localKart != null) {
            val sofaModel = assetRepo.getDrm2Model("models/props/velvet_sofa.drm")
            if (sofaModel != null) {
                drawDrm2Model(
                    model = sofaModel,
                    x = localKart.x - 28f, y = -0.02f, z = localKart.z - 34f,
                    yawDeg = 28f, pitchDeg = 0f, rollDeg = 0f,
                    scale = 18f,
                    useLod = true
                )
            }
            val chairModel = assetRepo.getDrm2Model("models/props/damask_chair.drm")
            if (chairModel != null) {
                drawDrm2Model(
                    model = chairModel,
                    x = localKart.x + 24f, y = -0.02f, z = localKart.z - 28f,
                    yawDeg = -35f, pitchDeg = 0f, rollDeg = 0f,
                    scale = 18f,
                    useLod = true
                )
            }
        }

        if (!garageActive) {
            // Elevated Track Support Columns (Textured Walnut Pillars)
            for (i in track.samples.indices step 10) {
                val s = track.samples[i]
                if (s.pos.y > 1.2f) {
                    val colH = s.pos.y
                    drawLitMesh(
                        mesh = unitCube,
                        texId = walnutTexId,
                        x = s.pos.x, y = colH * 0.5f, z = s.pos.z,
                        sx = 1.4f, sy = colH, sz = 1.4f,
                        r = 0.88f, g = 0.76f, b = 0.62f, a = 1f,
                        roughness = 0.6f
                    )
                }
            }

            // Kitchen Counter Dark Sink Gap Cutout
            if (track.hasSinkGapZone) {
                val cx = (track.sinkGapMinX + track.sinkGapMaxX) * 0.5f
                val cz = (track.sinkGapMinZ + track.sinkGapMaxZ) * 0.5f
                val sx = track.sinkGapMaxX - track.sinkGapMinX
                val sz = track.sinkGapMaxZ - track.sinkGapMinZ
                drawLitMesh(
                    unitCube, assetRepo.getWhiteTexture(),
                    cx, 0.02f, cz, sx, 0.06f, sz,
                    0.04f, 0.06f, 0.10f, 1f,
                    metallic = 0.7f, roughness = 0.25f
                )
            }

            // Art Desk Paint Puddles
            for (puddle in track.paintPuddles) {
                val prgb = Meshes.hexToRgb(puddle.tintColorHex)
                drawLitMesh(
                    unitSphere, assetRepo.getWhiteTexture(),
                    x = puddle.x, y = 0.04f, z = puddle.z,
                    sx = puddle.radius * 2f, sy = 0.05f, sz = puddle.radius * 2f,
                    r = prgb[0], g = prgb[1], b = prgb[2], a = 0.92f,
                    roughness = 0.18f
                )
            }

            // Bathtub Rim Soap Slippery Zones
            for (soap in track.slipperyZones) {
                drawLitMesh(
                    unitSphere, assetRepo.getWhiteTexture(),
                    x = soap.x, y = 0.04f, z = soap.z,
                    sx = soap.radius * 2f, sy = 0.05f, sz = soap.radius * 2f,
                    r = 0.76f, g = 0.94f, b = 1.0f, a = 0.74f,
                    roughness = 0.15f
                )
            }
        }

        // 4. Draw Contact AO Shadow Rings under Landmarks, Pylons & Karts (Part D9)
        if (allowContactShadows) {
            drawAllContactShadows(track, snap, garageActive)
            GLES20.glUseProgram(litProgram)
        }

        if (!garageActive) {
            // 5. Start/Finish Overhead Gantry + 3 Countdown Lights + Flags
            drawStartFinishGantry(track.samples.first(), track.roadWidth, snap.countdownLightsLit)

            // 6. Draw Functional Ramps & Sticky Boost Pads (with boost_chevrons.png decal)
            for (prop in track.props) {
                drawFunctionalTrackProp(prop)
            }

            // 7. Draw Real-World Textured DRM2 Landmark Props from Track JSON (Part D4, D9, D10)
            for (placed in track.placedProps) {
                if (placed.isPylon) continue
                val dx = placed.x - camera.posX
                val dz = placed.z - camera.posZ
                val distSq = dx * dx + dz * dz
                if (distSq > 380f * 380f) continue
                val drm2 = assetRepo.getDrm2Model(placed.model)
                if (drm2 != null) {
                    val yawDeg = Math.toDegrees(placed.yawRad.toDouble()).toFloat()
                    drawDrm2Model(
                        model = drm2,
                        x = placed.x, y = placed.y, z = placed.z,
                        yawDeg = yawDeg, pitchDeg = 0f, rollDeg = 0f,
                        scale = placed.scale,
                        useLod = distSq > 80f * 80f || qualityLevel <= 0
                    )
                }
            }

            // 8. Draw Knockable Pylons (`chess_pawn_w`, `chess_pawn_b`, `traffic_cone`) with Tumble State (Part D10)
            for (pylon in snap.pylons) {
                val dx = pylon.x - camera.posX
                val dz = pylon.z - camera.posZ
                val distSq = dx * dx + dz * dz
                if (distSq > 260f * 260f) continue
                val drm2 = assetRepo.getDrm2Model(pylon.modelPath)
                if (drm2 != null) {
                    val yawDeg = Math.toDegrees(pylon.yawRad.toDouble()).toFloat()
                    val pitchDeg = Math.toDegrees(pylon.pitchRad.toDouble()).toFloat()
                    val rollDeg = Math.toDegrees(pylon.rollRad.toDouble()).toFloat()
                    drawDrm2Model(
                        model = drm2,
                        x = pylon.x, y = pylon.y, z = pylon.z,
                        yawDeg = yawDeg, pitchDeg = pitchDeg, rollDeg = rollDeg,
                        scale = pylon.scale,
                        useLod = distSq > 80f * 80f || qualityLevel <= 0
                    )
                }
            }

            // 9. Draw Dynamic Hazards
            for (haz in snap.dynamicHazards) {
                drawDynamicHazard(haz)
            }

            // 10. Draw Active Gold Coins (`models/coin.drm`)
            val drmCoin = assetRepo.getDrm1Model("models/coin.drm")
            for (coin in snap.coins) {
                if (!coin.active) continue
                val deg = Math.toDegrees(coin.rotationRad.toDouble()).toFloat()
                if (drmCoin != null) {
                    drawDrm1ModelSimple(
                        model = drmCoin,
                        x = coin.x, y = coin.y, z = coin.z,
                        yawDeg = deg,
                        scale = 2.4f,
                        paintRgb = floatArrayOf(1f, 0.84f, 0.1f),
                        alpha = 1f,
                        metallic = 0.75f,
                        roughness = 0.22f
                    )
                } else {
                    drawLitMeshRotated(
                        unitSphere, assetRepo.getWhiteTexture(),
                        x = coin.x, y = coin.y + 0.35f, z = coin.z,
                        rxDeg = 0f, ryDeg = deg, rzDeg = 0f,
                        sx = 0.85f, sy = 0.85f, sz = 0.25f,
                        r = 1.0f, g = 0.84f, b = 0.08f, a = 1f,
                        metallic = 0.75f, roughness = 0.22f
                    )
                }
            }

            // 11. Draw Item Boxes (`models/items/star_sticker.drm` spinning inside translucent crystal frame)
            val starModel = assetRepo.getDrm2Model("models/items/star_sticker.drm")
            for (box in snap.itemBoxes) {
                if (!box.active) continue
                val deg = Math.toDegrees(box.rotationRad.toDouble()).toFloat()
                val bobY = box.y + sin(box.rotationRad * 2f) * 0.18f
                if (starModel != null) {
                    drawDrm2Model(
                        model = starModel,
                        x = box.x, y = bobY - 0.55f, z = box.z,
                        yawDeg = deg * 1.4f, pitchDeg = 0f, rollDeg = 0f,
                        scale = 85f
                    )
                }
                drawLitMeshRotated(
                    rainbowCube, assetRepo.getWhiteTexture(),
                    x = box.x, y = bobY, z = box.z,
                    rxDeg = deg * 0.6f, ryDeg = deg, rzDeg = 25f,
                    sx = 1.25f, sy = 1.25f, sz = 1.25f,
                    r = 1f, g = 1f, b = 1f, a = if (starModel != null) 0.28f else 0.82f,
                    roughness = 0.18f
                )
            }

            // 12. Draw Time Trial Ghost Kart (translucent 42% alpha)
            val ghost = snap.ghostKart
            if (ghost != null) {
                drawGhostKart3D(ghost)
            }
        }

        // 13. Draw All Karts (DRM1 karts + 6th `CLASSIC_TOY` DRM2 kart + 3D held items)
        val isBattle = snap.gameMode == GameMode.BATTLE_BALLOONS
        for (kart in snap.karts) {
            if (garageActive && localKart != null && kart.slotIndex != localKart.slotIndex) continue
            if (!kart.isInvulnerableFlashing) {
                val alpha = if (kart.isGhostMode) 0.50f else 1.0f
                drawKart3D(
                    kart = kart,
                    forceAlpha = alpha,
                    isBattleMode = isBattle,
                    paintFinishIndex = if (kart.slotIndex == snap.localSlotIndex) garagePaintFinishIndex else 0
                )
            }
        }

        if (!garageActive) {
            // 14. Draw Active Projectiles using Real DRM2 Item Models
            for (proj in snap.projectiles) {
                val yawDeg = Math.toDegrees(proj.headingRad.toDouble()).toFloat()
                when (proj.kind) {
                    ProjectileKind.MARBLE -> {
                        val m = assetRepo.getDrm2Model("models/items/marble.drm")
                        val rollDeg = Math.toDegrees((proj.distanceTravelled / 0.6f).toDouble()).toFloat()
                        if (m != null) {
                            drawDrm2Model(m, proj.x, proj.y - 0.35f, proj.z, yawDeg, rollDeg, 0f, 100f)
                        } else {
                            drawLitMesh(unitSphere, assetRepo.getWhiteTexture(), proj.x, proj.y, proj.z, 0.7f, 0.7f, 0.7f, 0.13f, 0.85f, 0.42f, 1f, roughness = 0.12f)
                        }
                    }
                    ProjectileKind.MARBLE_HOMING -> {
                        val m = assetRepo.getDrm2Model("models/items/marble_homing.drm")
                        val rollDeg = Math.toDegrees((proj.distanceTravelled / 0.6f).toDouble()).toFloat()
                        if (m != null) {
                            drawDrm2Model(m, proj.x, proj.y - 0.35f, proj.z, yawDeg, rollDeg, 0f, 100f)
                        } else {
                            drawLitMesh(unitSphere, assetRepo.getWhiteTexture(), proj.x, proj.y, proj.z, 0.7f, 0.7f, 0.7f, 0.95f, 0.20f, 0.35f, 1f, roughness = 0.12f)
                        }
                    }
                    ProjectileKind.ERASER -> {
                        val m = assetRepo.getDrm2Model("models/items/eraser.drm")
                        if (m != null) {
                            drawDrm2Model(m, proj.x, proj.y - 0.3f, proj.z, yawDeg, 0f, 0f, 100f)
                        } else {
                            drawLitMesh(unitCube, assetRepo.getWhiteTexture(), proj.x, proj.y, proj.z, 1.3f, 0.55f, 0.85f, 0.96f, 0.35f, 0.55f, 1f)
                        }
                    }
                    ProjectileKind.TACK -> {
                        val m = assetRepo.getDrm2Model("models/items/thumbtack.drm")
                        if (m != null) {
                            drawDrm2Model(m, proj.x, proj.y - 0.3f, proj.z, yawDeg, 0f, 0f, 100f)
                        } else {
                            drawLitMesh(unitSphere, assetRepo.getWhiteTexture(), proj.x, proj.y, proj.z, 0.48f, 0.48f, 0.48f, 0.98f, 0.82f, 0.12f, 1f)
                        }
                    }
                    ProjectileKind.PAPER_PLANE -> {
                        val m = assetRepo.getDrm2Model("models/items/paper_plane.drm")
                        if (m != null) {
                            drawDrm2Model(m, proj.x, proj.y - 0.2f, proj.z, yawDeg, 0f, sin(snap.elapsedTimeSec * 6f) * 14f, 100f)
                        } else {
                            drawLitMeshRotated(
                                unitCube, assetRepo.getWhiteTexture(),
                                proj.x, proj.y, proj.z,
                                0f, yawDeg, 0f,
                                1.4f, 0.15f, 1.8f,
                                0.96f, 0.98f, 1.0f, 1f
                            )
                        }
                    }
                }
            }

            // Bathtub Rim translucent water plane (Track ID 5)
            if (track.id == 5) {
                drawLitMesh(
                    unitCube, assetRepo.getWhiteTexture(),
                    x = 0f, y = -0.35f, z = 0f,
                    sx = track.deskHalfExtentX * 1.4f, sy = 0.08f, sz = track.deskHalfExtentZ * 1.4f,
                    r = 0.22f, g = 0.80f, b = 0.98f, a = 0.48f,
                    roughness = 0.10f
                )
            }

            // 15. Update & Draw Pooled Unlit Particles (Sparks, Flames, Stall Smoke, Confetti, Dust)
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

                val stride = Meshes.UNLIT_STRIDE_BYTES
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
    }

    /**
     * Part D3: Renders start grid slot decals (`grid_box.png`), track surface decals (`track.decals`,
     * sorted back-to-front, 0.05 u above the desk with polygon offset), and drift skid trails
     * (`skid_mark.png`, 2000-quad ring buffer fading over 20 s).
     */
    private fun drawTrackDecalsAndGridBoxes(track: Track, snap: WorldRenderSnapshot) {
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-1f, -2f)
        GLES20.glDepthMask(false)

        // 1. Start Grid Slot Boxes (`textures/decals/grid_box.png`)
        val gridBoxTexId = assetRepo.getTextureId("textures/decals/grid_box.png", repeatWrap = false)
        for (slot in track.startGrid) {
            val yawDeg = Math.toDegrees(slot.headingRad.toDouble()).toFloat()
            drawLitMeshRotated(
                mesh = flatQuadXZ,
                texId = gridBoxTexId,
                x = slot.x,
                y = slot.y + 0.01f,
                z = slot.z,
                rxDeg = 0f,
                ryDeg = yawDeg,
                rzDeg = 0f,
                sx = 2.1f,
                sy = 1f,
                sz = 2.8f,
                r = 1f, g = 1f, b = 1f, a = 0.85f,
                roughness = 0.55f
            )
        }

        // 2. Track Decals (`track.decals`) sorted back-to-front, 0.05 u above desk
        val decals: List<TrackDecal> = track.decals
        val decalCount = decals.size
        if (decalCount > 0) {
            if (sortedDecalIndices.size < decalCount) {
                sortedDecalIndices = IntArray(decalCount)
                sortedDecalDistSq = FloatArray(decalCount)
            }
            for (i in 0 until decalCount) {
                val d = decals[i]
                val dx = d.x - camera.posX
                val dz = d.z - camera.posZ
                sortedDecalIndices[i] = i
                sortedDecalDistSq[i] = dx * dx + dz * dz
            }
            // Insertion sort back-to-front (descending distance squared)
            for (i in 1 until decalCount) {
                val keyIdx = sortedDecalIndices[i]
                val keyDist = sortedDecalDistSq[i]
                var j = i - 1
                while (j >= 0 && sortedDecalDistSq[j] < keyDist) {
                    sortedDecalIndices[j + 1] = sortedDecalIndices[j]
                    sortedDecalDistSq[j + 1] = sortedDecalDistSq[j]
                    j--
                }
                sortedDecalIndices[j + 1] = keyIdx
                sortedDecalDistSq[j + 1] = keyDist
            }

            for (i in 0 until decalCount) {
                val distSq = sortedDecalDistSq[i]
                if (distSq > 360f * 360f) continue
                val d = decals[sortedDecalIndices[i]]
                val texId = assetRepo.getTextureId(d.texture, repeatWrap = false)
                val yawDeg = Math.toDegrees(d.yawRad.toDouble()).toFloat()
                drawLitMeshRotated(
                    mesh = flatQuadXZ,
                    texId = texId,
                    x = d.x,
                    y = 0.03f, // 0.05 u above desk (-0.02f + 0.05f = 0.03f)
                    z = d.z,
                    rxDeg = 0f,
                    ryDeg = yawDeg,
                    rzDeg = 0f,
                    sx = d.size,
                    sy = 1f,
                    sz = d.size,
                    r = 1f, g = 1f, b = 1f, a = 0.92f,
                    roughness = 0.65f
                )
            }
        }

        // 3. Emit & Render Drift Skid Marks (`textures/decals/skid_mark.png`, 2000-quad ring buffer, 20 s fade)
        val nowSec = snap.elapsedTimeSec
        for (k in snap.karts) {
            val slot = k.slotIndex.coerceIn(0, GameConstants.MAX_RACERS - 1)
            if (k.isDrifting && k.speed > 6f && k.y < 6f) {
                val dx = k.x - lastKartSkidX[slot]
                val dz = k.z - lastKartSkidZ[slot]
                if (dx * dx + dz * dz >= 1.1f * 1.1f) {
                    lastKartSkidX[slot] = k.x
                    lastKartSkidZ[slot] = k.z
                    val yawRad = k.headingRad + k.visualYawOffsetRad
                    val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()
                    val rightX = cos(yawRad)
                    val rightZ = -sin(yawRad)
                    val fwdX = sin(yawRad)
                    val fwdZ = cos(yawRad)
                    // Left & right rear wheels
                    for (wheelSide in -1..1 step 2) {
                        val wx = k.x + rightX * (0.68f * wheelSide) - fwdX * 0.65f
                        val wz = k.z + rightZ * (0.68f * wheelSide) - fwdZ * 0.65f
                        skidX[skidWriteCursor] = wx
                        skidY[skidWriteCursor] = k.y + 0.04f
                        skidZ[skidWriteCursor] = wz
                        skidYawDeg[skidWriteCursor] = yawDeg
                        skidBirthSec[skidWriteCursor] = nowSec
                        skidWriteCursor = (skidWriteCursor + 1) % maxSkidMarks
                        if (skidTotalCount < maxSkidMarks) skidTotalCount++
                    }
                }
            }
        }

        if (skidTotalCount > 0) {
            val skidTexId = assetRepo.getTextureId("textures/decals/skid_mark.png", repeatWrap = false)
            for (i in 0 until skidTotalCount) {
                val age = nowSec - skidBirthSec[i]
                if (age < 0f || age >= 20f) continue
                val dx = skidX[i] - camera.posX
                val dz = skidZ[i] - camera.posZ
                if (dx * dx + dz * dz > 140f * 140f) continue
                val alpha = (1f - age / 20f).coerceIn(0f, 1f) * 0.62f
                drawLitMeshRotated(
                    mesh = flatQuadXZ,
                    texId = skidTexId,
                    x = skidX[i],
                    y = skidY[i],
                    z = skidZ[i],
                    rxDeg = 0f,
                    ryDeg = skidYawDeg[i],
                    rzDeg = 0f,
                    sx = 0.42f,
                    sy = 1f,
                    sz = 1.25f,
                    r = 0.12f, g = 0.12f, b = 0.14f, a = alpha,
                    roughness = 0.85f
                )
            }
        }

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
    }

    private fun drawBackdropDome(texId: Int, track: Track) {
        if (backdropProgram == 0) return
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(backdropProgram)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, camera.posX, camera.posY * 0.25f, camera.posZ)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(backdropMvpLoc, 1, false, mvpMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(backdropTexLoc, 0)
        if (backdropIsSrgbLoc >= 0) {
            GLES20.glUniform1f(backdropIsSrgbLoc, if (assetRepo.isTextureHardwareSrgb(texId)) 1f else 0f)
        }

        val tint = when {
            isGarageStageActive -> floatArrayOf(0.88f, 0.90f, 0.96f)
            track.lightingPreset == com.example.game.LightingPreset.NIGHT_RGB -> floatArrayOf(0.55f, 0.62f, 0.85f)
            track.lightingPreset == com.example.game.LightingPreset.MIDNIGHT_CITY -> floatArrayOf(0.52f, 0.58f, 0.78f)
            track.lightingPreset == com.example.game.LightingPreset.DUSK_LAMP ||
                track.lightingPreset == com.example.game.LightingPreset.LIBRARY_WARM -> floatArrayOf(0.95f, 0.86f, 0.76f)
            else -> floatArrayOf(1.0f, 1.0f, 1.0f)
        }
        GLES20.glUniform3f(backdropTintLoc, tint[0], tint[1], tint[2])

        val buf = backdropSphere.buffer
        val stride = Meshes.BACKDROP_STRIDE_BYTES
        buf.position(0)
        GLES20.glEnableVertexAttribArray(backdropPosLoc)
        GLES20.glVertexAttribPointer(backdropPosLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(backdropTexCoordLoc)
        GLES20.glVertexAttribPointer(backdropTexCoordLoc, 2, GLES20.GL_FLOAT, false, stride, buf)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, backdropSphere.vertexCount)
        GLES20.glDisableVertexAttribArray(backdropPosLoc)
        GLES20.glDisableVertexAttribArray(backdropTexCoordLoc)
        GLES20.glDepthMask(true)
    }

    private fun drawAllContactShadows(track: Track, snap: WorldRenderSnapshot, garageActive: Boolean) {
        if (unlitProgram == 0) return
        GLES20.glUseProgram(unlitProgram)
        GLES20.glDepthMask(false)

        val buf = contactShadowDisc.buffer
        val stride = Meshes.UNLIT_STRIDE_BYTES
        buf.position(0)
        GLES20.glEnableVertexAttribArray(unlitPosLoc)
        GLES20.glVertexAttribPointer(unlitPosLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(unlitColorLoc)
        GLES20.glVertexAttribPointer(unlitColorLoc, 4, GLES20.GL_FLOAT, false, stride, buf)

        fun drawDisc(x: Float, y: Float, z: Float, sx: Float, sz: Float, yawDeg: Float, alphaScale: Float) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, x, y, z)
            if (yawDeg != 0f) Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, sx, 1f, sz)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(unlitMvpLoc, 1, false, mvpMatrix, 0)
            GLES20.glUniform4f(unlitTintLoc, 1f, 1f, 1f, alphaScale)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, contactShadowDisc.vertexCount)
        }

        if (!garageActive) {
            // Contact AO under placed landmarks (0.6 u halo + prop radius, Part D9)
            for (p in track.placedProps) {
                if (p.isPylon) continue
                val dx = p.x - camera.posX
                val dz = p.z - camera.posZ
                if (dx * dx + dz * dz > 220f * 220f) continue
                val diam = (p.radius * 2.0f + 1.2f).coerceIn(2.5f, 36f)
                drawDisc(p.x, p.y + 0.025f, p.z, diam, diam, 0f, 0.85f)
            }

            // Contact AO under knockable pylons
            for (py in snap.pylons) {
                val dx = py.x - camera.posX
                val dz = py.z - camera.posZ
                if (dx * dx + dz * dz > 160f * 160f) continue
                val diam = (py.radius * 2.0f + 0.6f).coerceIn(2.2f, 6.0f)
                drawDisc(py.x, py.y.coerceAtLeast(0f) + 0.045f, py.z, diam, diam, 0f, 0.78f)
            }
        }

        // Soft Oval Contact Shadow under every Kart
        for (k in snap.karts) {
            if (garageActive && k.slotIndex != snap.localSlotIndex) continue
            val yawDeg = Math.toDegrees((k.headingRad + k.visualYawOffsetRad).toDouble()).toFloat()
            val groundY = if (garageActive) k.y else track.findNearestSampleWindowed(k.x, k.y, k.z, -1).sample.pos.y
            val heightAboveRoad = (k.y - groundY).coerceAtLeast(0f)
            val fade = (1f - heightAboveRoad * 0.14f).coerceIn(0.25f, 1f)
            val shrink = (1f - heightAboveRoad * 0.06f).coerceIn(0.65f, 1f)
            val alpha = if (k.isGhostMode) 0.45f * fade else 0.95f * fade
            drawDisc(
                x = k.x,
                y = groundY + 0.055f,
                z = k.z,
                sx = 1.95f * shrink,
                sz = 2.65f * shrink,
                yawDeg = yawDeg,
                alphaScale = alpha
            )
        }

        GLES20.glDepthMask(true)
    }

    private fun drawStartFinishGantry(s0: SplineSample, roadWidth: Float, countdownLightsLit: Int) {
        val sYawDeg = Math.toDegrees(s0.headingRad.toDouble()).toFloat()
        val halfW = roadWidth * 0.5f + 1.15f
        val leftX = s0.pos.x - s0.rightX * halfW
        val leftZ = s0.pos.z - s0.rightZ * halfW
        val rightX = s0.pos.x + s0.rightX * halfW
        val rightZ = s0.pos.z + s0.rightZ * halfW
        val whiteTex = assetRepo.getWhiteTexture()

        drawLitMesh(
            unitCube, whiteTex,
            x = leftX, y = s0.pos.y + 3.0f, z = leftZ,
            sx = 0.7f, sy = 6.0f, sz = 0.7f,
            r = 0.95f, g = 0.18f, b = 0.36f, a = 1f,
            metallic = 0.4f, roughness = 0.35f
        )
        drawLitMesh(
            unitCube, whiteTex,
            x = rightX, y = s0.pos.y + 3.0f, z = rightZ,
            sx = 0.7f, sy = 6.0f, sz = 0.7f,
            r = 0.95f, g = 0.18f, b = 0.36f, a = 1f,
            metallic = 0.4f, roughness = 0.35f
        )
        drawLitMeshRotated(
            unitCube, assetRepo.getTextureId("textures/surfaces/checker_finish.png", repeatWrap = true),
            x = s0.pos.x, y = s0.pos.y + 5.6f, z = s0.pos.z,
            rxDeg = 0f, ryDeg = sYawDeg, rzDeg = 0f,
            sx = halfW * 2.15f, sy = 0.95f, sz = 0.75f,
            r = 1f, g = 1f, b = 1f, a = 1f,
            roughness = 0.4f
        )

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
                unitSphere, whiteTex,
                x = lx, y = s0.pos.y + 4.9f, z = lz,
                sx = 0.85f, sy = 0.85f, sz = 0.85f,
                r = lr, g = lg, b = lb, a = 1f,
                roughness = 0.2f
            )
        }

        val drmFlag = assetRepo.getDrm1Model("models/flag.drm")
        if (drmFlag != null) {
            drawDrm1ModelSimple(drmFlag, leftX, s0.pos.y + 6.0f, leftZ, sYawDeg, 2.2f, floatArrayOf(1f, 0.8f, 0.1f), 1f)
            drawDrm1ModelSimple(drmFlag, rightX, s0.pos.y + 6.0f, rightZ, sYawDeg, 2.2f, floatArrayOf(0f, 0.95f, 0.82f), 1f)
        }
    }

    private fun drawDynamicHazard(haz: RenderDynamicHazardSnapshot) {
        val c = Meshes.hexToRgb(haz.colorHex)
        val yawDeg = Math.toDegrees(haz.rotationRad.toDouble()).toFloat()
        when (haz.kind) {
            DynamicHazardKind.ROLLING_PEA -> {
                val m = assetRepo.getDrm2Model("models/items/marble.drm")
                if (m != null) {
                    drawDrm2Model(m, haz.x, haz.y - 0.4f, haz.z, yawDeg, yawDeg * 2f, 0f, 150f)
                } else {
                    drawLitMesh(unitSphere, assetRepo.getWhiteTexture(), haz.x, haz.y, haz.z, haz.radius * 2f, haz.radius * 2f, haz.radius * 2f, c[0], c[1], c[2], 1f)
                }
            }
            DynamicHazardKind.SCISSORS_GATE -> {
                drawLitMeshRotated(
                    unitCube, assetRepo.getWhiteTexture(),
                    haz.x, haz.y + 0.8f, haz.z,
                    0f, yawDeg, 25f,
                    haz.radius * 2.4f, 1.8f, 0.35f,
                    c[0], c[1], c[2], 1f,
                    metallic = 0.8f, roughness = 0.22f
                )
            }
            DynamicHazardKind.SPINNING_TOP -> {
                val pawn = assetRepo.getDrm2Model("models/props/chess_queen_b.drm")
                if (pawn != null) {
                    drawDrm2Model(pawn, haz.x, haz.y - 0.8f, haz.z, yawDeg * 4f, 10f, 0f, 45f)
                } else {
                    drawLitMeshRotated(
                        unitSphere, assetRepo.getWhiteTexture(),
                        haz.x, haz.y + 0.6f, haz.z,
                        12f, yawDeg, 0f,
                        haz.radius * 2.1f, 1.4f, haz.radius * 2.1f,
                        c[0], c[1], c[2], 1f
                    )
                }
            }
        }
    }

    private fun drawFunctionalTrackProp(prop: TrackProp) {
        val c1 = Meshes.hexToRgb(prop.colorHex)
        val yawDeg = Math.toDegrees(prop.yawRad.toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(prop.pitchRad.toDouble()).toFloat()

        when (prop.kind) {
            PropKind.RULER_RAMP -> {
                val rulerTex = assetRepo.getTextureId("textures/surfaces/ruler_strip.png", repeatWrap = false)
                drawLitMeshRotated(
                    unitCube, rulerTex,
                    prop.x, prop.y + prop.sy * 0.35f, prop.z,
                    pitchDeg, yawDeg, 0f,
                    prop.sx, prop.sy * 0.3f, prop.sz,
                    1f, 1f, 1f, 1f,
                    roughness = 0.4f
                )
            }
            PropKind.LAPTOP_BRIDGE, PropKind.CUTTING_BOARD -> {
                drawLitMeshRotated(
                    unitCube, assetRepo.getWhiteTexture(),
                    prop.x, prop.y + prop.sy * 0.35f, prop.z,
                    pitchDeg, yawDeg, 0f,
                    prop.sx, prop.sy * 0.3f, prop.sz,
                    c1[0], c1[1], c1[2], 1f,
                    metallic = 0.6f, roughness = 0.3f
                )
            }
            PropKind.STICKY_BOOST_PAD -> {
                // Part D3: Use `textures/decals/boost_chevrons.png` on boost pads
                val boostChevronTex = assetRepo.getTextureId("textures/decals/boost_chevrons.png", repeatWrap = false)
                drawLitMeshRotated(
                    unitCube, assetRepo.getWhiteTexture(),
                    prop.x, prop.y + prop.sy * 0.4f, prop.z,
                    0f, yawDeg, 0f,
                    prop.sx, prop.sy * 0.8f, prop.sz,
                    c1[0], c1[1], c1[2], 0.85f,
                    roughness = 0.25f
                )
                GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
                GLES20.glPolygonOffset(-1f, -2f)
                drawLitMeshRotated(
                    flatQuadXZ, boostChevronTex,
                    prop.x, prop.y + prop.sy + 0.02f, prop.z,
                    0f, yawDeg, 0f,
                    prop.sx * 0.92f, 1f, prop.sz * 0.92f,
                    1f, 1f, 1f, 0.98f,
                    roughness = 0.20f
                )
                GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
            }
            PropKind.PENNANT_FLAG -> {
                val drmFlag = assetRepo.getDrm1Model("models/flag.drm")
                if (drmFlag != null) {
                    drawDrm1ModelSimple(drmFlag, prop.x, prop.y, prop.z, yawDeg, prop.sy * 0.45f, c1, 1f)
                }
            }
            PropKind.STEAM_CLOUD -> {
                val drmCloud = assetRepo.getDrm1Model("models/cloud.drm")
                if (drmCloud != null) {
                    drawDrm1ModelSimple(drmCloud, prop.x, prop.y, prop.z, yawDeg, prop.sx * 0.45f, c1, 0.82f)
                }
            }
            else -> Unit
        }
    }

    private fun drawGhostKart3D(ghost: RenderGhostSnapshot) {
        val yawDeg = Math.toDegrees(ghost.headingRad.toDouble()).toFloat()
        val bodyRgb = Meshes.hexToRgb(ghost.colorHex)
        if (ghost.bodyType.isDrm2) {
            val drm2 = assetRepo.getDrm2Model(ghost.bodyType.drmAssetPath)
            if (drm2 != null) {
                drawDrm2Model(drm2, ghost.x, ghost.y, ghost.z, yawDeg, 0f, 0f, scale = 58f, forceAlpha = 0.42f)
                return
            }
        }
        val drmModel = assetRepo.getDrm1Model(ghost.bodyType.drmAssetPath)
        if (drmModel != null) {
            drawDrm1ModelSimple(
                model = drmModel,
                x = ghost.x, y = ghost.y, z = ghost.z,
                yawDeg = yawDeg,
                scale = 1.32f,
                paintRgb = bodyRgb,
                alpha = 0.42f
            )
        } else {
            drawLitMeshRotated(
                unitCube, assetRepo.getWhiteTexture(),
                x = ghost.x, y = ghost.y + 0.4f, z = ghost.z,
                rxDeg = 0f, ryDeg = yawDeg, rzDeg = 0f,
                sx = 1.42f, sy = 0.45f, sz = 2.20f,
                r = bodyRgb[0], g = bodyRgb[1], b = bodyRgb[2], a = 0.42f
            )
        }
    }

    private fun drawKart3D(
        kart: RenderKartSnapshot,
        forceAlpha: Float,
        isBattleMode: Boolean,
        paintFinishIndex: Int = 0
    ) {
        val totalYawRad = kart.headingRad + kart.visualYawOffsetRad
        val yawDeg = Math.toDegrees(totalYawRad.toDouble()).toFloat()
        val rollDeg = Math.toDegrees(kart.visualRollRad.toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(kart.visualPitchRad.toDouble()).toFloat()
        val activeColorHex = if (kart.paintTintHex != 0L) kart.paintTintHex else kart.colorHex
        val baseRgb = Meshes.hexToRgb(activeColorHex)

        // Part E5: Paint Finish (0=Gloss, 1=Matte, 2=Metallic, 3=Pearl -> roughness/metalness uniforms)
        val (finishMetallic, finishRoughness) = when (paintFinishIndex % 4) {
            1 -> Pair(0.08f, 0.78f) // Matte
            2 -> Pair(0.88f, 0.16f) // Metallic
            3 -> Pair(0.62f, 0.12f) // Pearl
            else -> Pair(0.28f, 0.22f) // Gloss
        }

        // Apply livery variation for DRM1 karts or hue-shift for CLASSIC_TOY DRM2 kart
        val hueShiftRad = (kart.liveryIndex % 3) * 2.094395f // 0°, +120°, +240°
        val bodyRgb = when (kart.liveryIndex % 3) {
            1 -> floatArrayOf(
                (baseRgb[0] * 0.7f + baseRgb[2] * 0.45f).coerceIn(0f, 1f),
                (baseRgb[1] * 0.85f + baseRgb[0] * 0.25f).coerceIn(0f, 1f),
                (baseRgb[2] * 0.7f + baseRgb[1] * 0.45f).coerceIn(0f, 1f)
            )
            2 -> floatArrayOf(
                (baseRgb[0] * 0.55f + 0.42f).coerceIn(0f, 1f),
                (baseRgb[1] * 0.55f + 0.42f).coerceIn(0f, 1f),
                (baseRgb[2] * 0.55f + 0.45f).coerceIn(0f, 1f)
            )
            else -> baseRgb
        }

        if (kart.bodyType.isDrm2) {
            val toyCarModel = assetRepo.getDrm2Model(kart.bodyType.drmAssetPath)
            if (toyCarModel != null) {
                drawDrm2Model(
                    model = toyCarModel,
                    x = kart.x, y = kart.y, z = kart.z,
                    yawDeg = yawDeg, pitchDeg = pitchDeg, rollDeg = rollDeg,
                    scale = 58f,
                    hueShiftRad = hueShiftRad,
                    forceAlpha = forceAlpha,
                    overrideMetallic = finishMetallic,
                    overrideRoughness = finishRoughness
                )
            }
        } else {
            val drmModel = assetRepo.getDrm1Model(kart.bodyType.drmAssetPath)
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

                    renderDrm1PartBuffer(
                        part = part,
                        matrix = partMatrix,
                        paintR = bodyRgb[0],
                        paintG = bodyRgb[1],
                        paintB = bodyRgb[2],
                        alpha = forceAlpha,
                        metallic = if (part.isWheel) 0.1f else finishMetallic,
                        roughness = if (part.isWheel) 0.75f else finishRoughness
                    )
                }
            } else {
                // Procedural Fallback if DRM asset is missing
                val whiteTex = assetRepo.getWhiteTexture()
                fun drawLocalPart(lx: Float, ly: Float, lz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float, mesh: GlMesh = unitCube) {
                    Matrix.setIdentityM(modelMatrix, 0)
                    Matrix.translateM(modelMatrix, 0, kart.x, kart.y, kart.z)
                    Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
                    if (pitchDeg != 0f) Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
                    if (rollDeg != 0f) Matrix.rotateM(modelMatrix, 0, rollDeg, 0f, 0f, 1f)
                    Matrix.translateM(modelMatrix, 0, lx, ly, lz)
                    Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
                    renderLitMeshBuffer(mesh, whiteTex, r, g, b, forceAlpha, metallic = finishMetallic, roughness = finishRoughness)
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
        }

        // Driver Helmet & Visor (for open-cockpit DRM1 karts)
        val whiteTex = assetRepo.getWhiteTexture()
        fun drawKartAccessory(lx: Float, ly: Float, lz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float, mesh: GlMesh) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, kart.x, kart.y, kart.z)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            if (pitchDeg != 0f) Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
            if (rollDeg != 0f) Matrix.rotateM(modelMatrix, 0, rollDeg, 0f, 0f, 1f)
            Matrix.translateM(modelMatrix, 0, lx, ly, lz)
            Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
            renderLitMeshBuffer(mesh, whiteTex, r, g, b, forceAlpha, metallic = finishMetallic, roughness = finishRoughness)
        }

        if (!kart.bodyType.isDrm2) {
            drawKartAccessory(0f, 1.08f, -0.08f, 0.44f, 0.44f, 0.44f, bodyRgb[0], bodyRgb[1], bodyRgb[2], unitSphere)
            drawKartAccessory(0f, 1.10f, 0.28f, 0.48f, 0.16f, 0.18f, 0.15f, 0.75f, 0.98f, unitCube)
        }

        // Held 3D Items using Real DRM2 Item Models
        val fwdX = sin(totalYawRad)
        val fwdZ = cos(totalYawRad)

        if (kart.shieldEraserHeld || kart.heldItem == ItemType.ERASER) {
            val eraserModel = assetRepo.getDrm2Model("models/items/eraser.drm")
            val ex = kart.x - fwdX * 1.75f
            val ez = kart.z - fwdZ * 1.75f
            if (eraserModel != null) {
                drawDrm2Model(eraserModel, ex, kart.y + 0.15f, ez, yawDeg, 0f, 0f, 95f, forceAlpha = forceAlpha)
            } else {
                drawKartAccessory(0f, 0.42f, -1.75f, 1.2f, 0.5f, 0.7f, 0.96f, 0.35f, 0.55f, unitCube)
            }
        }

        if (kart.orbitingTacksCount > 0) {
            val tackModel = assetRepo.getDrm2Model("models/items/thumbtack.drm")
            for (i in 0 until kart.orbitingTacksCount) {
                val ang = (System.currentTimeMillis() * 0.005 + i * 2.09).toFloat()
                val tx = kart.x + cos(ang) * 1.65f
                val tz = kart.z + sin(ang) * 1.65f
                if (tackModel != null) {
                    drawDrm2Model(tackModel, tx, kart.y + 0.15f, tz, ang * 57f, 0f, 0f, 95f, forceAlpha = forceAlpha)
                } else {
                    drawKartAccessory(cos(ang) * 1.65f, 0.45f, sin(ang) * 1.65f, 0.35f, 0.35f, 0.35f, 0.98f, 0.82f, 0.12f, unitSphere)
                }
            }
        }

        if (kart.heldItem == ItemType.BATTERY || kart.isBoosting) {
            val batteryModel = assetRepo.getDrm2Model("models/items/battery.drm")
            if (batteryModel != null) {
                val bx = kart.x - fwdX * 1.35f
                val bz = kart.z - fwdZ * 1.35f
                drawDrm2Model(batteryModel, bx, kart.y + 0.35f, bz, yawDeg, 0f, 0f, 85f, forceAlpha = forceAlpha)
            }
        }

        if (kart.heldItem == ItemType.MAGNET) {
            val magnetModel = assetRepo.getDrm2Model("models/items/magnet.drm")
            if (magnetModel != null) {
                val mx = kart.x + fwdX * 1.45f
                val mz = kart.z + fwdZ * 1.45f
                drawDrm2Model(magnetModel, mx, kart.y + 0.45f, mz, yawDeg, 0f, 0f, 85f, forceAlpha = forceAlpha)
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

    private fun drawDrm2Model(
        model: Drm2Model,
        x: Float, y: Float, z: Float,
        yawDeg: Float, pitchDeg: Float, rollDeg: Float,
        scale: Float,
        useLod: Boolean = false,
        hueShiftRad: Float = 0f,
        forceAlpha: Float = 1f,
        overrideMetallic: Float = -1f,
        overrideRoughness: Float = -1f
    ) {
        if (litTangentLoc >= 0) {
            GLES20.glDisableVertexAttribArray(litTangentLoc)
            GLES20.glVertexAttrib3f(litTangentLoc, 1f, 0f, 0f)
        }
        for (part in model.parts) {
            Matrix.setIdentityM(partMatrix, 0)
            Matrix.translateM(partMatrix, 0, x, y, z)
            if (yawDeg != 0f) Matrix.rotateM(partMatrix, 0, yawDeg, 0f, 1f, 0f)
            if (pitchDeg != 0f) Matrix.rotateM(partMatrix, 0, pitchDeg, 1f, 0f, 0f)
            if (rollDeg != 0f) Matrix.rotateM(partMatrix, 0, rollDeg, 0f, 0f, 1f)
            Matrix.scaleM(partMatrix, 0, scale, scale, scale)
            Matrix.translateM(partMatrix, 0, part.pivotX, part.pivotY, part.pivotZ)

            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, partMatrix, 0)
            GLES20.glUniformMatrix4fv(litMvpLoc, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(litModelLoc, 1, false, partMatrix, 0)

            val texId = if (part.texPath.isNotEmpty()) {
                assetRepo.getTextureId(part.texPath, repeatWrap = true)
            } else {
                assetRepo.getWhiteTexture()
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)

            val bcf = part.baseColorFactor
            GLES20.glUniform4f(litBaseColorFactorLoc, bcf[0], bcf[1], bcf[2], bcf[3] * forceAlpha)
            GLES20.glUniform3f(litPaintColorLoc, 1f, 1f, 1f)
            GLES20.glUniform4f(litTintLoc, 1f, 1f, 1f, 1f)

            val alphaCutoff = if (part.alphaMode == 1) 0.5f else 0.0f
            val m = if (overrideMetallic >= 0f) overrideMetallic else part.metallic
            val r = if (overrideRoughness >= 0f) overrideRoughness else part.roughness
            GLES20.glUniform4f(litMaterialParamsLoc, m, r, alphaCutoff, hueShiftRad)
            if (litNormalParamsLoc >= 0) {
                val texSrgb = if (assetRepo.isTextureHardwareSrgb(texId)) 1f else 0f
                GLES20.glUniform4f(litNormalParamsLoc, 0f, texSrgb, currentEnvIsSrgb, 0f)
            }

            val vBuf = part.vertexBuffer
            val stride = Drm2Loader.DRM2_STRIDE_BYTES

            vBuf.position(0)
            GLES20.glEnableVertexAttribArray(litPosLoc)
            GLES20.glVertexAttribPointer(litPosLoc, 3, GLES20.GL_FLOAT, false, stride, vBuf)

            vBuf.position(3)
            GLES20.glEnableVertexAttribArray(litNormLoc)
            GLES20.glVertexAttribPointer(litNormLoc, 3, GLES20.GL_FLOAT, false, stride, vBuf)

            if (litTexCoordLoc >= 0) {
                vBuf.position(6)
                GLES20.glEnableVertexAttribArray(litTexCoordLoc)
                GLES20.glVertexAttribPointer(litTexCoordLoc, 2, GLES20.GL_FLOAT, false, stride, vBuf)
            }

            vBuf.position(8)
            GLES20.glEnableVertexAttribArray(litVertColorLoc)
            GLES20.glVertexAttribPointer(litVertColorLoc, 3, GLES20.GL_FLOAT, false, stride, vBuf)

            if (litPaintMaskLoc >= 0) {
                vBuf.position(11)
                GLES20.glEnableVertexAttribArray(litPaintMaskLoc)
                GLES20.glVertexAttribPointer(litPaintMaskLoc, 1, GLES20.GL_FLOAT, false, stride, vBuf)
            }

            val iBuf = if (useLod) part.lodIndexBuffer else part.indexBuffer
            val iCount = if (useLod) part.lodIndexCount else part.indexCount
            iBuf.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, iCount, GLES20.GL_UNSIGNED_SHORT, iBuf)
        }

        if (litPaintMaskLoc >= 0) {
            GLES20.glDisableVertexAttribArray(litPaintMaskLoc)
            GLES20.glVertexAttrib1f(litPaintMaskLoc, 0f)
        }
    }

    private fun drawDrm1ModelSimple(
        model: DrmModel,
        x: Float, y: Float, z: Float,
        yawDeg: Float,
        scale: Float,
        paintRgb: FloatArray,
        alpha: Float,
        metallic: Float = 0.25f,
        roughness: Float = 0.35f
    ) {
        for (part in model.parts) {
            Matrix.setIdentityM(partMatrix, 0)
            Matrix.translateM(partMatrix, 0, x, y, z)
            if (yawDeg != 0f) Matrix.rotateM(partMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(partMatrix, 0, scale, scale, scale)
            Matrix.translateM(partMatrix, 0, part.pivotX, part.pivotY, part.pivotZ)
            renderDrm1PartBuffer(part, partMatrix, paintRgb[0], paintRgb[1], paintRgb[2], alpha, metallic, roughness)
        }
    }

    private fun renderDrm1PartBuffer(
        part: DrmPartMesh,
        matrix: FloatArray,
        paintR: Float,
        paintG: Float,
        paintB: Float,
        alpha: Float,
        metallic: Float = 0.25f,
        roughness: Float = 0.35f
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, matrix, 0)
        GLES20.glUniformMatrix4fv(litMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(litModelLoc, 1, false, matrix, 0)

        val whiteTex = assetRepo.getWhiteTexture()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, whiteTex)

        GLES20.glUniform4f(litBaseColorFactorLoc, 1f, 1f, 1f, 1f)
        GLES20.glUniform3f(litPaintColorLoc, paintR, paintG, paintB)
        GLES20.glUniform4f(litTintLoc, 1f, 1f, 1f, alpha)
        GLES20.glUniform4f(litMaterialParamsLoc, metallic, roughness, 0f, 0f)
        if (litNormalParamsLoc >= 0) {
            val texSrgb = if (assetRepo.isTextureHardwareSrgb(whiteTex)) 1f else 0f
            GLES20.glUniform4f(litNormalParamsLoc, 0f, texSrgb, currentEnvIsSrgb, 0f)
        }

        val buf = part.buffer
        val stride = DrmModelLoader.DRM_STRIDE_BYTES

        buf.position(0)
        GLES20.glEnableVertexAttribArray(litPosLoc)
        GLES20.glVertexAttribPointer(litPosLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(litNormLoc)
        GLES20.glVertexAttribPointer(litNormLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        if (litTexCoordLoc >= 0) {
            GLES20.glDisableVertexAttribArray(litTexCoordLoc)
            GLES20.glVertexAttrib2f(litTexCoordLoc, 0f, 0f)
        }
        if (litTangentLoc >= 0) {
            GLES20.glDisableVertexAttribArray(litTangentLoc)
            GLES20.glVertexAttrib3f(litTangentLoc, 1f, 0f, 0f)
        }

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
        texId: Int,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float,
        metallic: Float = 0.05f,
        roughness: Float = 0.55f
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        renderLitMeshBuffer(mesh, texId, r, g, b, a, metallic, roughness)
    }

    private fun drawLitMeshRotated(
        mesh: GlMesh,
        texId: Int,
        x: Float, y: Float, z: Float,
        rxDeg: Float, ryDeg: Float, rzDeg: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float,
        metallic: Float = 0.05f,
        roughness: Float = 0.55f
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (ryDeg != 0f) Matrix.rotateM(modelMatrix, 0, ryDeg, 0f, 1f, 0f)
        if (rxDeg != 0f) Matrix.rotateM(modelMatrix, 0, rxDeg, 1f, 0f, 0f)
        if (rzDeg != 0f) Matrix.rotateM(modelMatrix, 0, rzDeg, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        renderLitMeshBuffer(mesh, texId, r, g, b, a, metallic, roughness)
    }

    private fun renderLitMeshBuffer(
        mesh: GlMesh,
        texId: Int,
        r: Float, g: Float, b: Float, a: Float,
        metallic: Float = 0.05f,
        roughness: Float = 0.55f,
        normalTexId: Int = 0,
        normalMapStrength: Float = 0f
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(litMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(litModelLoc, 1, false, modelMatrix, 0)

        if (normalMapStrength > 0.01f && normalTexId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, normalTexId)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)

        GLES20.glUniform4f(litBaseColorFactorLoc, 1f, 1f, 1f, 1f)
        GLES20.glUniform3f(litPaintColorLoc, 1f, 1f, 1f)
        GLES20.glUniform4f(litTintLoc, r, g, b, a)
        GLES20.glUniform4f(litMaterialParamsLoc, metallic, roughness, 0f, 0f)
        if (litNormalParamsLoc >= 0) {
            val texSrgb = if (assetRepo.isTextureHardwareSrgb(texId)) 1f else 0f
            val effNormStr = if (normalTexId != 0) normalMapStrength else 0f
            GLES20.glUniform4f(litNormalParamsLoc, effNormStr, texSrgb, currentEnvIsSrgb, 0f)
        }

        val buf = mesh.buffer
        val stride = Meshes.LIT_STRIDE_BYTES

        buf.position(0)
        GLES20.glEnableVertexAttribArray(litPosLoc)
        GLES20.glVertexAttribPointer(litPosLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(litNormLoc)
        GLES20.glVertexAttribPointer(litNormLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        if (litTexCoordLoc >= 0) {
            buf.position(6)
            GLES20.glEnableVertexAttribArray(litTexCoordLoc)
            GLES20.glVertexAttribPointer(litTexCoordLoc, 2, GLES20.GL_FLOAT, false, stride, buf)
        }

        buf.position(8)
        GLES20.glEnableVertexAttribArray(litVertColorLoc)
        GLES20.glVertexAttribPointer(litVertColorLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        if (litPaintMaskLoc >= 0) {
            GLES20.glDisableVertexAttribArray(litPaintMaskLoc)
            GLES20.glVertexAttrib1f(litPaintMaskLoc, 0f)
        }

        if (litTangentLoc >= 0) {
            val tanBuf = mesh.tangentBuffer
            if (tanBuf != null) {
                tanBuf.position(0)
                GLES20.glEnableVertexAttribArray(litTangentLoc)
                GLES20.glVertexAttribPointer(litTangentLoc, 3, GLES20.GL_FLOAT, false, 3 * 4, tanBuf)
            } else {
                GLES20.glDisableVertexAttribArray(litTangentLoc)
                GLES20.glVertexAttrib3f(litTangentLoc, 1f, 0f, 0f)
            }
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)

        if (litTangentLoc >= 0 && mesh.tangentBuffer != null) {
            GLES20.glDisableVertexAttribArray(litTangentLoc)
            GLES20.glVertexAttrib3f(litTangentLoc, 1f, 0f, 0f)
        }
    }
}
