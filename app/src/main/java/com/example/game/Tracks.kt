package com.example.game

object Tracks {

    private fun buildTrackWithJson(
        id: Int,
        cupId: Int,
        slug: String,
        defaultName: String,
        subtitle: String,
        difficultyStars: Int,
        difficultyLabel: String,
        defaultRoadWidth: Float,
        deskSurfaceHex: Long,
        roadSurfaceHex: Long,
        kerbPrimaryHex: Long,
        kerbSecondaryHex: Long,
        skyTopHex: Long,
        skyBottomHex: Long,
        fogHex: Long,
        defaultSurfaceTex: String,
        defaultRoadTex: String,
        defaultBackdropTex: String,
        defaultLighting: LightingPreset,
        defaultMusic: String,
        goldTimeMs: Long,
        silverTimeMs: Long,
        bronzeTimeMs: Long,
        deskHalfExtentX: Float = 165f,
        deskHalfExtentZ: Float = 152f,
        hasSinkGapZone: Boolean = false,
        sinkGapMinX: Float = 0f,
        sinkGapMaxX: Float = 0f,
        sinkGapMinZ: Float = 0f,
        sinkGapMaxZ: Float = 0f,
        hasWaterCenterPool: Boolean = false,
        waterPoolRadiusX: Float = 90f,
        waterPoolRadiusZ: Float = 48f,
        hasRgbKerbs: Boolean = false,
        bankedSegmentRange: ClosedFloatingPointRange<Float>? = null,
        fallbackControlPoints: List<Vec3>,
        rawProps: List<TrackProp> = emptyList(),
        paintPuddles: List<PaintPuddleZone> = emptyList(),
        slipperyZones: List<SlipperyZone> = emptyList(),
        dynamicHazards: List<DynamicHazardSpec> = emptyList()
    ): Track {
        val parsed = TrackJsonLoader.loadTrackJson(slug)
        return Track(
            id = id,
            cupId = cupId,
            slug = slug,
            name = parsed?.name ?: defaultName,
            subtitle = subtitle,
            difficultyStars = difficultyStars,
            difficultyLabel = difficultyLabel,
            roadWidth = parsed?.roadWidth ?: defaultRoadWidth,
            deskSurfaceHex = deskSurfaceHex,
            roadSurfaceHex = roadSurfaceHex,
            kerbPrimaryHex = kerbPrimaryHex,
            kerbSecondaryHex = kerbSecondaryHex,
            skyTopHex = skyTopHex,
            skyBottomHex = skyBottomHex,
            fogHex = fogHex,
            surfaceTexture = parsed?.surfaceTexture ?: defaultSurfaceTex,
            surfaceNormal = parsed?.surfaceNormal ?: defaultSurfaceTex.replace(".jpg", "_n.jpg"),
            surfaceTextureTileUnits = parsed?.surfaceTextureTileUnits ?: 60f,
            roadTexture = parsed?.roadTexture ?: defaultRoadTex,
            roadNormal = parsed?.roadNormal ?: defaultRoadTex.replace(".jpg", "_n.jpg"),
            roadTextureTileUnits = parsed?.roadTextureTileUnits ?: 24f,
            kerbTexture = parsed?.kerbTexture ?: "textures/surfaces/kerb_red_white.png",
            finishTexture = parsed?.finishTexture ?: "textures/surfaces/checker_finish.png",
            backdropEquirect = parsed?.backdropEquirect ?: defaultBackdropTex,
            previewCard = parsed?.previewCard ?: "textures/ui/tracks/$slug.jpg",
            lightingPreset = parsed?.lightingPreset ?: defaultLighting,
            musicAssetPath = parsed?.music ?: defaultMusic,
            goldTimeMs = goldTimeMs,
            silverTimeMs = silverTimeMs,
            bronzeTimeMs = bronzeTimeMs,
            deskHalfExtentX = deskHalfExtentX,
            deskHalfExtentZ = deskHalfExtentZ,
            hasSinkGapZone = hasSinkGapZone,
            sinkGapMinX = sinkGapMinX,
            sinkGapMaxX = sinkGapMaxX,
            sinkGapMinZ = sinkGapMinZ,
            sinkGapMaxZ = sinkGapMaxZ,
            hasWaterCenterPool = hasWaterCenterPool,
            waterPoolRadiusX = waterPoolRadiusX,
            waterPoolRadiusZ = waterPoolRadiusZ,
            hasRgbKerbs = hasRgbKerbs,
            bankedSegmentRange = bankedSegmentRange,
            splineSubdivisionsPerSegment = parsed?.splineSubdivisionsPerSegment ?: 18,
            controlPoints = parsed?.controlPoints ?: fallbackControlPoints,
            rawProps = rawProps,
            rawPlacedProps = parsed?.props ?: emptyList(),
            rawDecals = parsed?.decals ?: emptyList(),
            paintPuddles = paintPuddles,
            slipperyZones = slipperyZones,
            dynamicHazards = dynamicHazards
        )
    }

    // =========================================================================
    // DESK CUP (Tracks 0..3)
    // =========================================================================

    // 0. STUDY DESK — width 12, length ≈ 875, ★☆☆
    val studyDesk: Track by lazy {
        buildTrackWithJson(
            id = 0,
            cupId = 0,
            slug = "study_desk",
            defaultName = "STUDY DESK",
            subtitle = "Warm walnut desk • Ruled notebook road • Chess pawns & brass lamp",
            difficultyStars = 1,
            difficultyLabel = "★☆☆ EASY",
            defaultRoadWidth = 12f,
            deskSurfaceHex = 0xFF6B4226L,
            roadSurfaceHex = 0xFFE2E8F0L,
            kerbPrimaryHex = 0xFFEF4444L,
            kerbSecondaryHex = 0xFFF8FAFCL,
            skyTopHex = 0xFF1E1B4BL,
            skyBottomHex = 0xFF451A03L,
            fogHex = 0xFF271C19L,
            defaultSurfaceTex = "textures/surfaces/wood_walnut.jpg",
            defaultRoadTex = "textures/surfaces/paper_ruled.jpg",
            defaultBackdropTex = "textures/backdrops/room_apartment.jpg",
            defaultLighting = LightingPreset.DUSK_LAMP,
            defaultMusic = "audio/music/race_study_desk_loop.ogg",
            goldTimeMs = 88_000L,
            silverTimeMs = 99_000L,
            bronzeTimeMs = 114_000L,
            deskHalfExtentX = 175f,
            deskHalfExtentZ = 155f,
            fallbackControlPoints = listOf(
                Vec3(0f, 0f, 90f),
                Vec3(60f, 0f, 92f),
                Vec3(110f, 0f, 80f),
                Vec3(135f, 0f, 45f),
                Vec3(128f, 0f, 5f),
                Vec3(100f, 0f, -15f),
                Vec3(95f, 0f, -50f),
                Vec3(118f, 0f, -85f),
                Vec3(95f, 0f, -118f),
                Vec3(45f, 0f, -120f),
                Vec3(15f, 0f, -95f),
                Vec3(-20f, 3.5f, -95f),
                Vec3(-55f, 3.5f, -100f),
                Vec3(-95f, 0f, -110f),
                Vec3(-125f, 0f, -80f),
                Vec3(-120f, 0f, -40f),
                Vec3(-109f, 0f, -20f),
                Vec3(-118f, 0f, 15f),
                Vec3(-120f, 0f, 55f),
                Vec3(-90f, 0f, 85f),
                Vec3(-45f, 0f, 92f)
            ),
            rawProps = listOf(
                TrackProp(
                    kind = PropKind.RULER_RAMP,
                    x = -65f, y = 2.8f, z = -102f,
                    sx = 10f, sy = 1.4f, sz = 8f,
                    yawRad = -1.70f,
                    colorHex = 0xFFFACC15L,
                    secondaryHex = 0xFF1E293BL,
                    isSolidWall = false,
                    isRamp = true
                ),
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = 75f, y = 0.06f, z = 88f,
                    sx = 6.5f, sy = 0.14f, sz = 6.5f,
                    yawRad = 1.45f,
                    colorHex = 0xFFFEF08AL,
                    secondaryHex = 0xFFF97316L,
                    isSolidWall = false,
                    isBoostPad = true
                ),
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = -119f, y = 0.06f, z = 35f,
                    sx = 6.5f, sy = 0.14f, sz = 6.5f,
                    yawRad = 0.0f,
                    colorHex = 0xFF67E8F9L,
                    secondaryHex = 0xFF0284C7L,
                    isSolidWall = false,
                    isBoostPad = true
                )
            )
        )
    }

    // 1. GAMER DESK — width 11.5, length ≈ 1193, ★★☆
    val gamerDesk: Track by lazy {
        buildTrackWithJson(
            id = 1,
            cupId = 0,
            slug = "gamer_desk",
            defaultName = "GAMER DESK",
            subtitle = "Dark desk mat • Sci-fi helmet & stained-glass lamp • Figure-8 bridge",
            difficultyStars = 2,
            difficultyLabel = "★★☆ MEDIUM",
            defaultRoadWidth = 11.5f,
            deskSurfaceHex = 0xFF111827L,
            roadSurfaceHex = 0xFF1E293BL,
            kerbPrimaryHex = 0xFF06B6D4L,
            kerbSecondaryHex = 0xFFD946EFL,
            skyTopHex = 0xFF030712L,
            skyBottomHex = 0xFF1E1B4BL,
            fogHex = 0xFF090D16L,
            defaultSurfaceTex = "textures/surfaces/desk_mat_dark.jpg",
            defaultRoadTex = "textures/surfaces/road_asphalt.jpg",
            defaultBackdropTex = "textures/backdrops/room_studio.jpg",
            defaultLighting = LightingPreset.NIGHT_RGB,
            defaultMusic = "audio/music/race_gamer_desk_loop.ogg",
            goldTimeMs = 112_000L,
            silverTimeMs = 125_000L,
            bronzeTimeMs = 142_000L,
            deskHalfExtentX = 175f,
            deskHalfExtentZ = 155f,
            hasRgbKerbs = true,
            fallbackControlPoints = listOf(
                Vec3(0f, 0f, 100f),
                Vec3(55f, 0f, 100f),
                Vec3(105f, 0f, 92f),
                Vec3(130f, 0f, 60f),
                Vec3(115f, 0f, 25f),
                Vec3(80f, 0f, 20f),
                Vec3(55f, 0f, 0f),
                Vec3(70f, 0f, -30f),
                Vec3(110f, 0f, -40f),
                Vec3(130f, 0f, -75f),
                Vec3(105f, 0f, -110f),
                Vec3(60f, 0f, -115f),
                Vec3(25f, 0f, -90f),
                Vec3(0f, 4f, -60f),
                Vec3(-25f, 6f, -30f),
                Vec3(-50f, 6f, -5f),
                Vec3(-90f, 6f, -10f),
                Vec3(-125f, 4f, -40f),
                Vec3(-130f, 0f, -85f),
                Vec3(-100f, 0f, -115f),
                Vec3(-60f, 0f, -110f),
                Vec3(-40f, 0f, -80f),
                Vec3(-70f, 0f, -50f),
                Vec3(-110f, 0f, -20f),
                Vec3(-130f, 0f, 25f),
                Vec3(-115f, 0f, 70f),
                Vec3(-70f, 0f, 95f)
            ),
            rawProps = listOf(
                TrackProp(
                    kind = PropKind.LAPTOP_BRIDGE,
                    x = -105f, y = 4.6f, z = -22f,
                    sx = 9f, sy = 1.1f, sz = 8f,
                    yawRad = -2.2f,
                    colorHex = 0xFF64748BL,
                    secondaryHex = 0xFF38BDF8L,
                    isSolidWall = false,
                    isRamp = true
                ),
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = 40f, y = 0.06f, z = 100f,
                    sx = 7f, sy = 0.14f, sz = 6f,
                    yawRad = 1.57f,
                    colorHex = 0xFFA855F7L,
                    secondaryHex = 0xFF22D3EEL,
                    isSolidWall = false,
                    isBoostPad = true
                ),
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = -122f, y = 0.06f, z = 48f,
                    sx = 6f, sy = 0.14f, sz = 7f,
                    yawRad = 0.3f,
                    colorHex = 0xFF22C55EL,
                    secondaryHex = 0xFF06B6D4L,
                    isSolidWall = false,
                    isBoostPad = true
                )
            )
        )
    }

    // 2. KITCHEN COUNTER — width 10.5, length ≈ 1123, ★★★
    val kitchenCounter: Track by lazy {
        buildTrackWithJson(
            id = 2,
            cupId = 0,
            slug = "kitchen_counter",
            defaultName = "KITCHEN COUNTER",
            subtitle = "White marble counter • Cutting-board road • Oranges, avocado & olive dish",
            difficultyStars = 3,
            difficultyLabel = "★★★ HARD",
            defaultRoadWidth = 10.5f,
            deskSurfaceHex = 0xFFE2E8F0L,
            roadSurfaceHex = 0xFFD97706L,
            kerbPrimaryHex = 0xFFF97316L,
            kerbSecondaryHex = 0xFFF8FAFCL,
            skyTopHex = 0xFF0284C7L,
            skyBottomHex = 0xFFBAE6FDL,
            fogHex = 0xFFCBD5E1L,
            defaultSurfaceTex = "textures/surfaces/marble_white.jpg",
            defaultRoadTex = "textures/surfaces/cutting_board.jpg",
            defaultBackdropTex = "textures/backdrops/room_lobby.jpg",
            defaultLighting = LightingPreset.MORNING_SUN,
            defaultMusic = "audio/music/race_kitchen_loop.ogg",
            goldTimeMs = 108_000L,
            silverTimeMs = 120_000L,
            bronzeTimeMs = 136_000L,
            deskHalfExtentX = 175f,
            deskHalfExtentZ = 155f,
            hasSinkGapZone = true,
            sinkGapMinX = 132f,
            sinkGapMaxX = 156f,
            sinkGapMinZ = -105f,
            sinkGapMaxZ = -55f,
            fallbackControlPoints = listOf(
                Vec3(0f, 0f, 100f),
                Vec3(60f, 0f, 100f),
                Vec3(115f, 0f, 90f),
                Vec3(135f, 0f, 55f),
                Vec3(110f, 0f, 30f),
                Vec3(75f, 0f, 40f),
                Vec3(50f, 0f, 15f),
                Vec3(60f, 0f, -20f),
                Vec3(100f, 0f, -30f),
                Vec3(130f, 0f, -60f),
                Vec3(120f, 0f, -100f),
                Vec3(80f, 0f, -120f),
                Vec3(35f, 0f, -110f),
                Vec3(10f, 0f, -80f),
                Vec3(-20f, 0f, -90f),
                Vec3(-45f, 0f, -120f),
                Vec3(-90f, 0f, -115f),
                Vec3(-120f, 0f, -80f),
                Vec3(-100f, 0f, -45f),
                Vec3(-60f, 0f, -40f),
                Vec3(-45f, 0f, -10f),
                Vec3(-75f, 0f, 15f),
                Vec3(-120f, 0f, 25f),
                Vec3(-130f, 0f, 65f),
                Vec3(-95f, 0f, 95f),
                Vec3(-45f, 0f, 100f)
            ),
            rawProps = listOf(
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = 80f, y = 0.06f, z = -120f,
                    sx = 6.5f, sy = 0.14f, sz = 6.5f,
                    yawRad = -1.57f,
                    colorHex = 0xFFFACC15L,
                    secondaryHex = 0xFFF97316L,
                    isSolidWall = false,
                    isBoostPad = true
                )
            ),
            dynamicHazards = listOf(
                DynamicHazardSpec(
                    id = 1,
                    kind = DynamicHazardKind.ROLLING_PEA,
                    centerX = 80f,
                    centerY = 0.9f,
                    centerZ = -25f,
                    axisX = 0.25f,
                    axisZ = 0.96f,
                    amplitude = 5.0f,
                    periodSec = 3.2f,
                    radius = 1.1f,
                    phaseOffset = 0f,
                    colorHex = 0xFF22C55EL
                ),
                DynamicHazardSpec(
                    id = 2,
                    kind = DynamicHazardKind.ROLLING_PEA,
                    centerX = -80f,
                    centerY = 0.9f,
                    centerZ = -42f,
                    axisX = 0.1f,
                    axisZ = 0.99f,
                    amplitude = 5.0f,
                    periodSec = 2.8f,
                    radius = 1.1f,
                    phaseOffset = 1.4f,
                    colorHex = 0xFF16A34AL
                )
            )
        )
    }

    // 3. ART DESK — width 12, length ≈ 934, ★★☆
    val artDesk: Track by lazy {
        buildTrackWithJson(
            id = 3,
            cupId = 0,
            slug = "art_desk",
            defaultName = "ART DESK",
            subtitle = "Light oak art table • Painted paper road • Glass flower vases & puddles",
            difficultyStars = 2,
            difficultyLabel = "★★☆ MEDIUM",
            defaultRoadWidth = 12f,
            deskSurfaceHex = 0xFFD6B98CL,
            roadSurfaceHex = 0xFFF8FAFCL,
            kerbPrimaryHex = 0xFFEC4899L,
            kerbSecondaryHex = 0xFFFACC15L,
            skyTopHex = 0xFF0284C7L,
            skyBottomHex = 0xFFFEF08AL,
            fogHex = 0xFFFEF9C3L,
            defaultSurfaceTex = "textures/surfaces/wood_oak_light.jpg",
            defaultRoadTex = "textures/surfaces/paper_paint.jpg",
            defaultBackdropTex = "textures/backdrops/room_apartment.jpg",
            defaultLighting = LightingPreset.SUNNY_STUDIO,
            defaultMusic = "audio/music/race_study_desk_loop.ogg",
            goldTimeMs = 92_000L,
            silverTimeMs = 104_000L,
            bronzeTimeMs = 119_000L,
            deskHalfExtentX = 175f,
            deskHalfExtentZ = 155f,
            fallbackControlPoints = listOf(
                Vec3(0f, 0f, 95f),
                Vec3(55f, 0f, 95f),
                Vec3(100f, 0f, 75f),
                Vec3(110f, 0f, 35f),
                Vec3(85f, 0f, 5f),
                Vec3(95f, 0f, -30f),
                Vec3(125f, 0f, -55f),
                Vec3(110f, 0f, -95f),
                Vec3(70f, 0f, -110f),
                Vec3(40f, 0f, -85f),
                Vec3(45f, 0f, -50f),
                Vec3(15f, 0f, -30f),
                Vec3(-20f, 0f, -50f),
                Vec3(-25f, 0f, -90f),
                Vec3(-60f, 0f, -115f),
                Vec3(-105f, 0f, -100f),
                Vec3(-120f, 0f, -60f),
                Vec3(-95f, 0f, -25f),
                Vec3(-110f, 0f, 15f),
                Vec3(-105f, 0f, 60f),
                Vec3(-60f, 0f, 90f)
            ),
            paintPuddles = listOf(
                PaintPuddleZone(x = 15f, z = -30f, radius = 5.5f, tintColorHex = 0xFFEC4899L),
                PaintPuddleZone(x = -95f, z = -25f, radius = 5.5f, tintColorHex = 0xFF00F5D4L)
            ),
            dynamicHazards = listOf(
                DynamicHazardSpec(
                    id = 10,
                    kind = DynamicHazardKind.SCISSORS_GATE,
                    centerX = -25f,
                    centerY = 1.2f,
                    centerZ = -90f,
                    axisX = 0.85f,
                    axisZ = -0.52f,
                    amplitude = 3.2f,
                    periodSec = 2.5f,
                    radius = 1.4f,
                    colorHex = 0xFF94A3B8L
                )
            )
        )
    }

    // =========================================================================
    // ROOM CUP (Tracks 4..7)
    // =========================================================================

    // 4. BOOKSHELF SPIRAL — width 11, length ≈ 904, ★★★
    val bookshelfSpiral: Track by lazy {
        buildTrackWithJson(
            id = 4,
            cupId = 1,
            slug = "bookshelf_spiral",
            defaultName = "BOOKSHELF SPIRAL",
            subtitle = "Walnut library • Kraft cardboard spiral ramp • Chess pieces & brass lanterns",
            difficultyStars = 3,
            difficultyLabel = "★★★ HARD",
            defaultRoadWidth = 11f,
            deskSurfaceHex = 0xFF451A03L,
            roadSurfaceHex = 0xFFC29B6CL,
            kerbPrimaryHex = 0xFFFACC15L,
            kerbSecondaryHex = 0xFF7C2D12L,
            skyTopHex = 0xFF0F172AL,
            skyBottomHex = 0xFF3B0764L,
            fogHex = 0xFF1E1B4BL,
            defaultSurfaceTex = "textures/surfaces/wood_walnut.jpg",
            defaultRoadTex = "textures/surfaces/cardboard_kraft.jpg",
            defaultBackdropTex = "textures/backdrops/room_lobby.jpg",
            defaultLighting = LightingPreset.LIBRARY_WARM,
            defaultMusic = "audio/music/race_bonus_loop.ogg",
            goldTimeMs = 90_000L,
            silverTimeMs = 102_000L,
            bronzeTimeMs = 118_000L,
            deskHalfExtentX = 175f,
            deskHalfExtentZ = 155f,
            fallbackControlPoints = listOf(
                Vec3(0f, 0f, 90f),
                Vec3(55f, 0f, 90f),
                Vec3(95f, 0f, 72f),
                Vec3(108f, 0f, 30f),
                Vec3(80f, 4f, -20f),
                Vec3(40f, 8f, -35f),
                Vec3(0f, 12f, -25f),
                Vec3(-25f, 16f, 5f),
                Vec3(-15f, 20f, 40f),
                Vec3(20f, 22f, 50f),
                Vec3(50f, 22f, 30f),
                Vec3(55f, 18f, -10f),
                Vec3(35f, 14f, -55f),
                Vec3(0f, 10f, -80f),
                Vec3(-45f, 6f, -85f),
                Vec3(-85f, 3f, -65f),
                Vec3(-105f, 0f, -25f),
                Vec3(-110f, 0f, 25f),
                Vec3(-90f, 0f, 65f),
                Vec3(-50f, 0f, 88f)
            ),
            rawProps = listOf(
                TrackProp(
                    kind = PropKind.RULER_RAMP,
                    x = 50f, y = 22f, z = 25f,
                    sx = 8.5f, sy = 1.1f, sz = 7f,
                    yawRad = 3.0f,
                    colorHex = 0xFFFACC15L,
                    secondaryHex = 0xFF7C2D12L,
                    isSolidWall = false,
                    isRamp = true
                ),
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = -108f, y = 0.06f, z = 5f,
                    sx = 6.5f, sy = 0.14f, sz = 6.5f,
                    yawRad = 0f,
                    colorHex = 0xFFFACC15L,
                    secondaryHex = 0xFFF97316L,
                    isSolidWall = false,
                    isBoostPad = true
                )
            )
        )
    }

    // 5. BATHTUB RIM — width 10, length ≈ 737, ★☆☆
    val bathtubRim: Track by lazy {
        buildTrackWithJson(
            id = 5,
            cupId = 1,
            slug = "bathtub_rim",
            defaultName = "BATHTUB RIM",
            subtitle = "Ceramic tile oval • Shimmering bath water • Slippery soap zones",
            difficultyStars = 1,
            difficultyLabel = "★☆☆ FAST OVAL",
            defaultRoadWidth = 10f,
            deskSurfaceHex = 0xFFF1F5F9L,
            roadSurfaceHex = 0xFFE2E8F0L,
            kerbPrimaryHex = 0xFF38BDF8L,
            kerbSecondaryHex = 0xFFF8FAFCL,
            skyTopHex = 0xFF0284C7L,
            skyBottomHex = 0xFFE0F2FEL,
            fogHex = 0xFFBAE6FDL,
            defaultSurfaceTex = "textures/surfaces/tiles_bath.jpg",
            defaultRoadTex = "textures/surfaces/tiles_bath.jpg",
            defaultBackdropTex = "textures/backdrops/room_studio.jpg",
            defaultLighting = LightingPreset.BATHROOM_BRIGHT,
            defaultMusic = "audio/music/race_bonus_loop.ogg",
            goldTimeMs = 74_000L,
            silverTimeMs = 84_000L,
            bronzeTimeMs = 96_000L,
            deskHalfExtentX = 175f,
            deskHalfExtentZ = 140f,
            hasWaterCenterPool = true,
            waterPoolRadiusX = 92f,
            waterPoolRadiusZ = 44f,
            fallbackControlPoints = listOf(
                Vec3(0f, 0f, 80f),
                Vec3(50f, 0f, 82f),
                Vec3(95f, 0f, 75f),
                Vec3(125f, 0f, 50f),
                Vec3(132f, 0f, 10f),
                Vec3(125f, 0f, -30f),
                Vec3(100f, 0f, -60f),
                Vec3(60f, 0f, -72f),
                Vec3(30f, 0f, -55f),
                Vec3(0f, 0f, -70f),
                Vec3(-30f, 0f, -55f),
                Vec3(-60f, 0f, -72f),
                Vec3(-100f, 0f, -60f),
                Vec3(-125f, 0f, -30f),
                Vec3(-132f, 0f, 10f),
                Vec3(-125f, 0f, 50f),
                Vec3(-95f, 0f, 75f),
                Vec3(-50f, 0f, 82f)
            ),
            rawProps = listOf(
                TrackProp(
                    kind = PropKind.STEAM_CLOUD,
                    x = -38f, y = 6.5f, z = 10f,
                    sx = 12f, sy = 5f, sz = 12f,
                    colorHex = 0xFFF8FAFCL,
                    isSolidWall = false
                ),
                TrackProp(
                    kind = PropKind.STEAM_CLOUD,
                    x = 42f, y = 7.5f, z = -8f,
                    sx = 12f, sy = 5f, sz = 12f,
                    colorHex = 0xFFF8FAFCL,
                    isSolidWall = false
                ),
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = 75f, y = 0.06f, z = 79f,
                    sx = 6.5f, sy = 0.14f, sz = 6.5f,
                    yawRad = 1.50f,
                    colorHex = 0xFF38BDF8L,
                    secondaryHex = 0xFFFACC15L,
                    isSolidWall = false,
                    isBoostPad = true
                ),
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = -75f, y = 0.06f, z = 79f,
                    sx = 6.5f, sy = 0.14f, sz = 6.5f,
                    yawRad = 1.64f,
                    colorHex = 0xFF38BDF8L,
                    secondaryHex = 0xFFFACC15L,
                    isSolidWall = false,
                    isBoostPad = true
                )
            ),
            slipperyZones = listOf(
                SlipperyZone(x = 125f, z = -30f, radius = 8.0f),
                SlipperyZone(x = -125f, z = 50f, radius = 8.0f)
            )
        )
    }

    // 6. TOY BOX — width 12, length ≈ 943, ★★☆
    val toyBox: Track by lazy {
        buildTrackWithJson(
            id = 6,
            cupId = 1,
            slug = "toy_box",
            defaultName = "TOY BOX",
            subtitle = "Playroom floor rug • Elevated bridge • Banked S-bend & traffic cones",
            difficultyStars = 2,
            difficultyLabel = "★★☆ MEDIUM",
            defaultRoadWidth = 12f,
            deskSurfaceHex = 0xFF15803DL,
            roadSurfaceHex = 0xFF374151L,
            kerbPrimaryHex = 0xFFFACC15L,
            kerbSecondaryHex = 0xFFEF4444L,
            skyTopHex = 0xFF1D4ED8L,
            skyBottomHex = 0xFF93C5FDL,
            fogHex = 0xFFBFDBFEL,
            defaultSurfaceTex = "textures/surfaces/rug_playroom.jpg",
            defaultRoadTex = "textures/surfaces/road_asphalt.jpg",
            defaultBackdropTex = "textures/backdrops/room_apartment.jpg",
            defaultLighting = LightingPreset.PLAYROOM_SOFT,
            defaultMusic = "audio/music/race_kitchen_loop.ogg",
            goldTimeMs = 94_000L,
            silverTimeMs = 106_000L,
            bronzeTimeMs = 122_000L,
            deskHalfExtentX = 175f,
            deskHalfExtentZ = 155f,
            bankedSegmentRange = 0.14f..0.28f,
            fallbackControlPoints = listOf(
                Vec3(0f, 0f, 100f),
                Vec3(50f, 0f, 100f),
                Vec3(95f, 0f, 85f),
                Vec3(112f, 0f, 50f),
                Vec3(101f, 0f, 25f),
                Vec3(112f, 0f, 0f),
                Vec3(125f, 0f, -35f),
                Vec3(100f, 0f, -70f),
                Vec3(60f, 5f, -75f),
                Vec3(25f, 5f, -60f),
                Vec3(10f, 0f, -25f),
                Vec3(-20f, 0f, -5f),
                Vec3(-50f, 0f, -25f),
                Vec3(-55f, 0f, -65f),
                Vec3(-38f, 0f, -95f),
                Vec3(-60f, 0f, -125f),
                Vec3(-105f, 0f, -115f),
                Vec3(-125f, 0f, -75f),
                Vec3(-115f, 0f, -30f),
                Vec3(-125f, 0f, 15f),
                Vec3(-115f, 0f, 60f),
                Vec3(-75f, 0f, 92f),
                Vec3(-40f, 0f, 100f)
            ),
            rawProps = listOf(
                TrackProp(
                    kind = PropKind.PENNANT_FLAG,
                    x = 60f, y = 5f, z = -67f,
                    sx = 3f, sy = 5f, sz = 3f,
                    colorHex = 0xFFFACC15L,
                    isSolidWall = false
                ),
                TrackProp(
                    kind = PropKind.PENNANT_FLAG,
                    x = 25f, y = 5f, z = -52f,
                    sx = 3f, sy = 5f, sz = 3f,
                    colorHex = 0xFFEF4444L,
                    isSolidWall = false
                ),
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = -120f, y = 0.06f, z = -5f,
                    sx = 6.5f, sy = 0.14f, sz = 6.5f,
                    yawRad = 0f,
                    colorHex = 0xFFF97316L,
                    secondaryHex = 0xFFFACC15L,
                    isSolidWall = false,
                    isBoostPad = true
                )
            ),
            dynamicHazards = listOf(
                DynamicHazardSpec(
                    id = 20,
                    kind = DynamicHazardKind.SPINNING_TOP,
                    centerX = -82f,
                    centerY = 0.8f,
                    centerZ = -120f,
                    axisX = 0.3f,
                    axisZ = 0.95f,
                    amplitude = 4.2f,
                    periodSec = 3.0f,
                    radius = 1.3f,
                    colorHex = 0xFFEF4444L
                )
            )
        )
    }

    // 7. MIDNIGHT OFFICE — width 11, length ≈ 1065, ★★★ (4th race of Room Cup, replaces Workshop Bench)
    val midnightOffice: Track by lazy {
        buildTrackWithJson(
            id = 7,
            cupId = 1,
            slug = "midnight_office",
            defaultName = "MIDNIGHT OFFICE",
            subtitle = "Executive walnut desk • Stained-glass & brass lamps • Skyline panorama",
            difficultyStars = 3,
            difficultyLabel = "★★★ EXPERT",
            defaultRoadWidth = 11f,
            deskSurfaceHex = 0xFF5C3A21L,
            roadSurfaceHex = 0xFF1F2937L,
            kerbPrimaryHex = 0xFFFACC15L,
            kerbSecondaryHex = 0xFF111827L,
            skyTopHex = 0xFF0B1020L,
            skyBottomHex = 0xFF1E293BL,
            fogHex = 0xFF111827L,
            defaultSurfaceTex = "textures/surfaces/wood_walnut.jpg",
            defaultRoadTex = "textures/surfaces/road_asphalt.jpg",
            defaultBackdropTex = "textures/backdrops/city_day.jpg",
            defaultLighting = LightingPreset.MIDNIGHT_CITY,
            defaultMusic = "audio/music/race_gamer_desk_loop.ogg",
            goldTimeMs = 96_000L,
            silverTimeMs = 108_000L,
            bronzeTimeMs = 124_000L,
            deskHalfExtentX = 175f,
            deskHalfExtentZ = 155f,
            fallbackControlPoints = listOf(
                Vec3(0f, 0f, 100f),
                Vec3(60f, 0f, 100f),
                Vec3(115f, 0f, 95f),
                Vec3(135f, 0f, 60f),
                Vec3(120f, 0f, 20f),
                Vec3(88f, 0f, 10f),
                Vec3(68f, 0f, -15f),
                Vec3(82f, 0f, -45f),
                Vec3(120f, 0f, -50f),
                Vec3(135f, 0f, -85f),
                Vec3(110f, 0f, -120f),
                Vec3(55f, 0f, -120f),
                Vec3(20f, 0f, -100f),
                Vec3(-5f, 0f, -65f),
                Vec3(-35f, 0f, -45f),
                Vec3(-70f, 0f, -60f),
                Vec3(-88f, 0f, -98f),
                Vec3(-120f, 0f, -116f),
                Vec3(-140f, 0f, -78f),
                Vec3(-125f, 0f, -35f),
                Vec3(-109f, 0f, -15f),
                Vec3(-118f, 0f, 20f),
                Vec3(-130f, 0f, 60f),
                Vec3(-105f, 0f, 92f),
                Vec3(-55f, 0f, 100f)
            ),
            rawProps = listOf(
                TrackProp(
                    kind = PropKind.STICKY_BOOST_PAD,
                    x = -118f, y = 0.06f, z = 48f,
                    sx = 6.5f, sy = 0.14f, sz = 6.5f,
                    yawRad = 0.1f,
                    colorHex = 0xFFFACC15L,
                    secondaryHex = 0xFFEF4444L,
                    isSolidWall = false,
                    isBoostPad = true
                )
            )
        )
    }

    val workshopBench: Track get() = midnightOffice

    val all: List<Track> by lazy {
        listOf(
            studyDesk,
            gamerDesk,
            kitchenCounter,
            artDesk,
            bookshelfSpiral,
            bathtubRim,
            toyBox,
            midnightOffice
        )
    }

    val deskCupTracks: List<Track> by lazy {
        listOf(studyDesk, gamerDesk, kitchenCounter, artDesk)
    }
    val roomCupTracks: List<Track> by lazy {
        listOf(bookshelfSpiral, bathtubRim, toyBox, midnightOffice)
    }

    fun byId(id: Int): Track = when (id) {
        0 -> studyDesk
        1 -> gamerDesk
        2 -> kitchenCounter
        3 -> artDesk
        4 -> bookshelfSpiral
        5 -> bathtubRim
        6 -> toyBox
        7 -> midnightOffice
        else -> studyDesk
    }
}
