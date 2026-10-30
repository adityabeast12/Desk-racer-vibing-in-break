package com.example.game

object Tracks {

    // =========================================================================
    // DESK CUP (Tracks 0..3)
    // =========================================================================

    // E1. STUDY DESK — width 12, length ≈ 875, difficulty ★☆☆
    val studyDesk: Track by lazy {
        Track(
        id = 0,
        cupId = 0,
        name = "STUDY DESK",
        subtitle = "Warm walnut desk • Open notebook straight • Yellow ruler bridge",
        difficultyStars = 1,
        difficultyLabel = "★☆☆ EASY",
        roadWidth = 12f,
        deskSurfaceHex = 0xFF6B4226L, // Warm walnut desk
        roadSurfaceHex = 0xFF28303DL,
        kerbPrimaryHex = 0xFFEF4444L,
        kerbSecondaryHex = 0xFFF8FAFCL,
        skyTopHex = 0xFF1E1B4BL,
        skyBottomHex = 0xFF451A03L, // 4200K dusk brass desk lamp glow
        fogHex = 0xFF271C19L,
        musicAssetPath = "audio/music/race_study_desk_loop.ogg",
        goldTimeMs = 88_000L,
        silverTimeMs = 99_000L,
        bronzeTimeMs = 114_000L,
        deskHalfExtentX = 160f,
        deskHalfExtentZ = 148f,
        controlPoints = listOf(
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
            // Open Notebook decorative sheet under start straight (non-solid visual base)
            TrackProp(
                kind = PropKind.CUTTING_BOARD,
                x = 10f, y = -0.05f, z = 91f,
                sx = 110f, sy = 0.08f, sz = 28f,
                yawRad = 0.0f,
                colorHex = 0xFFF8FAFCL,
                secondaryHex = 0xFFEF4444L,
                isSolidWall = false
            ),
            // Textbook chicane outer walls (100,-15)->(95,-50)->(118,-85)
            TrackProp(
                kind = PropKind.BOOK,
                x = 122f, y = 0f, z = -32f,
                sx = 18f, sy = 5.2f, sz = 12f,
                yawRad = 0.22f,
                colorHex = 0xFF1D4ED8L,
                secondaryHex = 0xFFFDE68AL
            ),
            TrackProp(
                kind = PropKind.BOOK,
                x = 74f, y = 0f, z = -48f,
                sx = 16f, sy = 4.6f, sz = 11f,
                yawRad = -0.15f,
                colorHex = 0xFFB91C1CL,
                secondaryHex = 0xFFFEF3C7L
            ),
            TrackProp(
                kind = PropKind.BOOK,
                x = 138f, y = 0f, z = -82f,
                sx = 15f, sy = 5.8f, sz = 12f,
                yawRad = 0.35f,
                colorHex = 0xFF047857L,
                secondaryHex = 0xFFF8FAFCL
            ),
            // Yellow RULER bridge trick ramp exit at (-68, 2.5, -102)
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
            // Steaming Coffee MUG roundabout island inside (-125,-80)..(-109,-20) hairpin
            TrackProp(
                kind = PropKind.MUG_ROUNDABOUT,
                x = -92f, y = 0f, z = -54f,
                sx = 14f, sy = 11f, sz = 14f,
                colorHex = 0xFFF97316L,
                secondaryHex = 0xFF3E2723L,
                isRoundBumper = true
            ),
            TrackProp(
                kind = PropKind.STEAM_CLOUD,
                x = -92f, y = 12.5f, z = -54f,
                sx = 8f, sy = 4f, sz = 8f,
                colorHex = 0xFFF8FAFCL,
                isSolidWall = false
            ),
            // Oriented Pencil rails along outer edges (Part B1 verified)
            TrackProp(
                kind = PropKind.PENCIL_RAIL,
                x = 20f, y = 0f, z = 104f,
                sx = 56f, sy = 1.5f, sz = 1.6f,
                yawRad = 0.0f,
                colorHex = 0xFFFACC15L,
                secondaryHex = 0xFFF472B6L
            ),
            TrackProp(
                kind = PropKind.PENCIL_RAIL,
                x = -136f, y = 0f, z = 35f,
                sx = 44f, sy = 1.5f, sz = 1.6f,
                yawRad = 1.57f,
                colorHex = 0xFFFACC15L,
                secondaryHex = 0xFFF472B6L
            ),
            // Sticky-note boost pads at (75,0,90) and (-118,0,35) (Part E1)
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

    // E2. GAMER DESK — width 11.5, length ≈ 1193, ★★☆
    val gamerDesk: Track by lazy {
        Track(
        id = 1,
        cupId = 0,
        name = "GAMER DESK",
        subtitle = "Dark carbon RGB rig • Mechanical keyboard • Laptop figure-8 bridge",
        difficultyStars = 2,
        difficultyLabel = "★★☆ MEDIUM",
        roadWidth = 11.5f,
        deskSurfaceHex = 0xFF111827L,
        roadSurfaceHex = 0xFF1E293BL,
        kerbPrimaryHex = 0xFF06B6D4L,
        kerbSecondaryHex = 0xFFD946EFL,
        skyTopHex = 0xFF030712L,
        skyBottomHex = 0xFF1E1B4BL,
        fogHex = 0xFF090D16L,
        musicAssetPath = "audio/music/race_gamer_desk_loop.ogg",
        goldTimeMs = 112_000L,
        silverTimeMs = 125_000L,
        bronzeTimeMs = 142_000L,
        deskHalfExtentX = 160f,
        deskHalfExtentZ = 148f,
        hasRgbKerbs = true,
        controlPoints = listOf(
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
            // Mechanical keyboard keycap zone (55,0,0)->(110,-40)
            TrackProp(
                kind = PropKind.KEYBOARD_BUMP,
                x = 62f, y = 0.08f, z = -16f,
                sx = 9f, sy = 0.22f, sz = 9f,
                yawRad = 0.4f,
                colorHex = 0xFF334155L,
                secondaryHex = 0xFF22D3EEL,
                isSolidWall = false
            ),
            TrackProp(
                kind = PropKind.KEYBOARD_BUMP,
                x = 88f, y = 0.08f, z = -34f,
                sx = 9f, sy = 0.22f, sz = 9f,
                yawRad = 0.2f,
                colorHex = 0xFF334155L,
                secondaryHex = 0xFFF43F5EL,
                isSolidWall = false
            ),
            // Elevated laptop bridge jump lip
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
            // Mousepad straight boost chevrons
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
            ),
            // Dual-monitor back wall towers
            TrackProp(
                kind = PropKind.BOOK,
                x = 0f, y = 0f, z = 122f,
                sx = 46f, sy = 16f, sz = 4f,
                colorHex = 0xFF0F172AL,
                secondaryHex = 0xFF06B6D4L
            )
        )
    )
    }

    // E3. KITCHEN COUNTER — width 10.5, length ≈ 1123, ★★★
    val kitchenCounter: Track by lazy {
        Track(
        id = 2,
        cupId = 0,
        name = "KITCHEN COUNTER",
        subtitle = "White marble counter • Bouncy oranges • Sink edge & rolling peas",
        difficultyStars = 3,
        difficultyLabel = "★★★ HARD",
        roadWidth = 10.5f,
        deskSurfaceHex = 0xFFE2E8F0L, // White marble counter
        roadSurfaceHex = 0xFF334155L,
        kerbPrimaryHex = 0xFFF97316L,
        kerbSecondaryHex = 0xFFF8FAFCL,
        skyTopHex = 0xFF0284C7L,
        skyBottomHex = 0xFFBAE6FDL, // Cool 6500K morning sunlight
        fogHex = 0xFFCBD5E1L,
        musicAssetPath = "audio/music/race_kitchen_loop.ogg",
        goldTimeMs = 108_000L,
        silverTimeMs = 120_000L,
        bronzeTimeMs = 136_000L,
        deskHalfExtentX = 160f,
        deskHalfExtentZ = 148f,
        hasSinkGapZone = true,
        sinkGapMinX = 132f,
        sinkGapMaxX = 156f,
        sinkGapMinZ = -105f,
        sinkGapMaxZ = -55f,
        controlPoints = listOf(
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
            // Cutting-board straight
            TrackProp(
                kind = PropKind.CUTTING_BOARD,
                x = 30f, y = -0.04f, z = 100f,
                sx = 68f, sy = 0.08f, sz = 18f,
                colorHex = 0xFFD97706L,
                secondaryHex = 0xFFFEF3C7L,
                isSolidWall = false
            ),
            // Kettle island inside (75,40)->(50,15) bend (placed safely inside the bend off the road)
            TrackProp(
                kind = PropKind.KETTLE_PILLAR,
                x = 80f, y = 0f, z = 10f,
                sx = 12f, sy = 14f, sz = 12f,
                colorHex = 0xFF94A3B8L,
                secondaryHex = 0xFF1E293BL,
                isRoundBumper = true
            ),
            // Bouncy Orange Bumpers (restitution 0.8, placed off racing line per Part B2)
            TrackProp(
                kind = PropKind.ORANGE_BUMPER,
                x = 102f, y = 0f, z = -92f,
                sx = 7.5f, sy = 7.5f, sz = 7.5f,
                colorHex = 0xFFF97316L,
                secondaryHex = 0xFF22C55EL,
                isRoundBumper = true,
                restitution = 0.8f
            ),
            TrackProp(
                kind = PropKind.ORANGE_BUMPER,
                x = -72f, y = 0f, z = -92f,
                sx = 7.5f, sy = 7.5f, sz = 7.5f,
                colorHex = 0xFFF97316L,
                secondaryHex = 0xFF22C55EL,
                isRoundBumper = true,
                restitution = 0.8f
            ),
            TrackProp(
                kind = PropKind.ORANGE_BUMPER,
                x = -102f, y = 0f, z = 62f,
                sx = 7.5f, sy = 7.5f, sz = 7.5f,
                colorHex = 0xFFF97316L,
                secondaryHex = 0xFF22C55EL,
                isRoundBumper = true,
                restitution = 0.8f
            ),
            // Boost pads
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
            // Rolling peas crossing the road on a timer (Part E3)
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

    // E4. ART DESK — width 12, length ≈ 934, ★★☆
    val artDesk: Track by lazy {
        Track(
        id = 3,
        cupId = 0,
        name = "ART DESK",
        subtitle = "Sunny art table • Paint puddles tint & boost • Scissors gate",
        difficultyStars = 2,
        difficultyLabel = "★★☆ MEDIUM",
        roadWidth = 12f,
        deskSurfaceHex = 0xFFFEFCE8L, // Sunny paper-covered art table
        roadSurfaceHex = 0xFF312E81L, // Painted indigo stripe road
        kerbPrimaryHex = 0xFFEC4899L,
        kerbSecondaryHex = 0xFFFACC15L,
        skyTopHex = 0xFF0284C7L,
        skyBottomHex = 0xFFFEF08AL,
        fogHex = 0xFFFEF9C3L,
        musicAssetPath = "audio/music/race_study_desk_loop.ogg",
        goldTimeMs = 92_000L,
        silverTimeMs = 104_000L,
        bronzeTimeMs = 119_000L,
        deskHalfExtentX = 155f,
        deskHalfExtentZ = 145f,
        controlPoints = listOf(
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
        rawProps = listOf(
            // Paint-palette island
            TrackProp(
                kind = PropKind.PAINT_PALETTE,
                x = 78f, y = 0f, z = -68f,
                sx = 22f, sy = 1.6f, sz = 16f,
                yawRad = 0.3f,
                colorHex = 0xFFD97706L,
                secondaryHex = 0xFFEC4899L
            ),
            // Crayon rail barriers (wax colours)
            TrackProp(
                kind = PropKind.CRAYON_RAIL,
                x = 25f, y = 0f, z = 109f,
                sx = 48f, sy = 2.0f, sz = 2.2f,
                yawRad = 0f,
                colorHex = 0xFFEF4444L,
                secondaryHex = 0xFF1E293BL
            ),
            TrackProp(
                kind = PropKind.CRAYON_RAIL,
                x = -124f, y = 0f, z = 38f,
                sx = 44f, sy = 2.0f, sz = 2.2f,
                yawRad = 1.50f,
                colorHex = 0xFF22C55EL,
                secondaryHex = 0xFF1E293BL
            ),
            // Glue-stick bollard on the inside of a corner
            TrackProp(
                kind = PropKind.GLUE_BOLLARD,
                x = 64f, y = 0f, z = 12f,
                sx = 6.5f, sy = 10f, sz = 6.5f,
                colorHex = 0xFFF8FAFCL,
                secondaryHex = 0xFF3B82F6L,
                isRoundBumper = true
            )
        ),
        // Paint puddles at (15,-30) and (-95,-25): tint kart for 5s and grant mini-turbo! (Part E4)
        paintPuddles = listOf(
            PaintPuddleZone(x = 15f, z = -30f, radius = 5.5f, tintColorHex = 0xFFEC4899L),
            PaintPuddleZone(x = -95f, z = -25f, radius = 5.5f, tintColorHex = 0xFF00F5D4L)
        ),
        // Scissors gate opens and closes slowly (2.5s cycle) at (-25,-90) leaving 6u inside gap
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

    // E5. BOOKSHELF SPIRAL — width 11, length ≈ 904, ★★★
    val bookshelfSpiral: Track by lazy {
        Track(
        id = 4,
        cupId = 1,
        name = "BOOKSHELF SPIRAL",
        subtitle = "Towering rainbow book spines • Elevated spiral summit crossover",
        difficultyStars = 3,
        difficultyLabel = "★★★ HARD",
        roadWidth = 11f,
        deskSurfaceHex = 0xFF451A03L,
        roadSurfaceHex = 0xFF1E293BL,
        kerbPrimaryHex = 0xFFFACC15L,
        kerbSecondaryHex = 0xFF7C2D12L,
        skyTopHex = 0xFF0F172AL,
        skyBottomHex = 0xFF3B0764L,
        fogHex = 0xFF1E1B4BL,
        musicAssetPath = "audio/music/race_bonus_loop.ogg",
        goldTimeMs = 90_000L,
        silverTimeMs = 102_000L,
        bronzeTimeMs = 118_000L,
        deskHalfExtentX = 150f,
        deskHalfExtentZ = 140f,
        controlPoints = listOf(
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
            // Rainbow book spine towers flanking the spiral
            TrackProp(
                kind = PropKind.BOOK,
                x = 12f, y = 0f, z = 12f,
                sx = 18f, sy = 20f, sz = 14f,
                yawRad = 0.25f,
                colorHex = 0xFF7C3AEDL,
                secondaryHex = 0xFFFACC15L
            ),
            TrackProp(
                kind = PropKind.BOOK,
                x = -65f, y = 0f, z = 20f,
                sx = 16f, sy = 16f, sz = 16f,
                yawRad = -0.4f,
                colorHex = 0xFFDC2626L,
                secondaryHex = 0xFFFACC15L
            ),
            // Summit trick ramp at (50, 22, 30)
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

    // E6. BATHTUB RIM — width 10, length ≈ 737, ★☆☆ (the fast one)
    val bathtubRim: Track by lazy {
        Track(
        id = 5,
        cupId = 1,
        name = "BATHTUB RIM",
        subtitle = "Ceramic oval • Animated water & rubber duck • Slippery soap zone",
        difficultyStars = 1,
        difficultyLabel = "★☆☆ FAST OVAL",
        roadWidth = 10f,
        deskSurfaceHex = 0xFFF1F5F9L, // White ceramic tile deck
        roadSurfaceHex = 0xFFE2E8F0L, // Glossy porcelain rim
        kerbPrimaryHex = 0xFF38BDF8L,
        kerbSecondaryHex = 0xFFF8FAFCL,
        skyTopHex = 0xFF0284C7L,
        skyBottomHex = 0xFFE0F2FEL,
        fogHex = 0xFFBAE6FDL,
        musicAssetPath = "audio/music/race_kitchen_loop.ogg",
        goldTimeMs = 74_000L,
        silverTimeMs = 84_000L,
        bronzeTimeMs = 96_000L,
        deskHalfExtentX = 155f,
        deskHalfExtentZ = 120f,
        hasWaterCenterPool = true,
        waterPoolRadiusX = 92f,
        waterPoolRadiusZ = 44f,
        controlPoints = listOf(
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
            // Giant Floating Rubber Duck in the center water pool (Part E6)
            TrackProp(
                kind = PropKind.RUBBER_DUCK,
                x = 0f, y = -0.5f, z = 5f,
                sx = 18f, sy = 16f, sz = 20f,
                yawRad = 0.6f,
                colorHex = 0xFFFACC15L,
                secondaryHex = 0xFFF97316L,
                isSolidWall = false
            ),
            // Steam cloud puffs over the warm bath
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
            // Shampoo-bottle pillars on the outside
            TrackProp(
                kind = PropKind.SHAMPOO_BOTTLE,
                x = 146f, y = 0f, z = 10f,
                sx = 9f, sy = 20f, sz = 9f,
                colorHex = 0xFF06B6D4L,
                secondaryHex = 0xFFF8FAFCL,
                isRoundBumper = true
            ),
            TrackProp(
                kind = PropKind.SHAMPOO_BOTTLE,
                x = -146f, y = 0f, z = 10f,
                sx = 9f, sy = 20f, sz = 9f,
                colorHex = 0xFFEC4899L,
                secondaryHex = 0xFFF8FAFCL,
                isRoundBumper = true
            ),
            // Boost pads on rim
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
        // Soap-bar slippery zone (grip x0.4) at (125, -30) and (-125, 50)
        slipperyZones = listOf(
            SlipperyZone(x = 125f, z = -30f, radius = 8.0f),
            SlipperyZone(x = -125f, z = 50f, radius = 8.0f)
        )
    )
    }

    // E7. TOY BOX — width 12, length ≈ 943, ★★☆
    val toyBox: Track by lazy {
        Track(
        id = 6,
        cupId = 1,
        name = "TOY BOX",
        subtitle = "Playroom floor rug • Letter-block bridge • Banked orange S-bend",
        difficultyStars = 2,
        difficultyLabel = "★★☆ MEDIUM",
        roadWidth = 12f,
        deskSurfaceHex = 0xFF15803DL, // Playroom rug green
        roadSurfaceHex = 0xFF374151L,
        kerbPrimaryHex = 0xFFFACC15L,
        kerbSecondaryHex = 0xFFEF4444L,
        skyTopHex = 0xFF1D4ED8L,
        skyBottomHex = 0xFF93C5FDL,
        fogHex = 0xFFBFDBFEL,
        musicAssetPath = "audio/music/race_bonus_loop.ogg",
        goldTimeMs = 94_000L,
        silverTimeMs = 106_000L,
        bronzeTimeMs = 122_000L,
        deskHalfExtentX = 158f,
        deskHalfExtentZ = 148f,
        bankedSegmentRange = 0.14f..0.28f, // Banked S-bend at (112,50)->(101,25)->(112,0)
        controlPoints = listOf(
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
            // Primary-colour letter blocks beside the Toy-Block Bridge
            TrackProp(
                kind = PropKind.TOY_BLOCK,
                x = 42f, y = 0f, z = -94f,
                sx = 12f, sy = 12f, sz = 12f,
                yawRad = 0.15f,
                colorHex = 0xFFEF4444L,
                secondaryHex = 0xFFFACC15L
            ),
            TrackProp(
                kind = PropKind.TOY_BLOCK,
                x = 76f, y = 0f, z = -52f,
                sx = 12f, sy = 12f, sz = 12f,
                yawRad = -0.25f,
                colorHex = 0xFF3B82F6L,
                secondaryHex = 0xFFF8FAFCL
            ),
            // Jack-in-the-box beside the road (pops up on a 4s cycle, Part E7)
            TrackProp(
                kind = PropKind.JACK_IN_BOX,
                x = -20f, y = 0f, z = 16f,
                sx = 10f, sy = 10f, sz = 10f,
                colorHex = 0xFFA855F7L,
                secondaryHex = 0xFFFACC15L
            ),
            // Pennant flags (flag.drm) along the bridge & start
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
            // Spinning top hazard on a lane (Part E7)
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

    // E8. WORKSHOP BENCH — width 11.5, length ≈ 965, ★★★ (4th race of Room Cup)
    val workshopBench: Track by lazy {
        Track(
        id = 7,
        cupId = 1,
        name = "WORKSHOP BENCH",
        subtitle = "Oak workbench • Sawhorse ramp • Wrench arch & tape-measure chicane",
        difficultyStars = 3,
        difficultyLabel = "★★★ EXPERT",
        roadWidth = 11.5f,
        deskSurfaceHex = 0xFF78350FL,
        roadSurfaceHex = 0xFF1F2937L,
        kerbPrimaryHex = 0xFFFACC15L,
        kerbSecondaryHex = 0xFF111827L,
        skyTopHex = 0xFF0F172AL,
        skyBottomHex = 0xFF334155L,
        fogHex = 0xFF1E293BL,
        musicAssetPath = "audio/music/race_gamer_desk_loop.ogg",
        goldTimeMs = 96_000L,
        silverTimeMs = 108_000L,
        bronzeTimeMs = 124_000L,
        deskHalfExtentX = 158f,
        deskHalfExtentZ = 148f,
        controlPoints = listOf(
            Vec3(0f, 0f, 95f),
            Vec3(60f, 0f, 95f),
            Vec3(110f, 0f, 80f),
            Vec3(130f, 0f, 40f),
            Vec3(115f, 4.5f, -5f),
            Vec3(85f, 4.5f, -35f),
            Vec3(95f, 0f, -75f),
            Vec3(120f, 0f, -105f),
            Vec3(80f, 0f, -125f),
            Vec3(35f, 0f, -105f),
            Vec3(15f, 0f, -65f),
            Vec3(-20f, 0f, -55f),
            Vec3(-45f, 0f, -95f),
            Vec3(-85f, 0f, -115f),
            Vec3(-125f, 0f, -90f),
            Vec3(-115f, 0f, -45f),
            Vec3(-85f, 0f, -15f),
            Vec3(-115f, 0f, 25f),
            Vec3(-120f, 0f, 70f),
            Vec3(-70f, 0f, 92f)
        ),
        rawProps = listOf(
            TrackProp(
                kind = PropKind.WRENCH_ARCH,
                x = 85f, y = 4.5f, z = -35f,
                sx = 14f, sy = 9f, sz = 4f,
                yawRad = 0.6f,
                colorHex = 0xFF94A3B8L,
                secondaryHex = 0xFFEF4444L,
                isSolidWall = false
            ),
            TrackProp(
                kind = PropKind.RULER_RAMP,
                x = 90f, y = 3.2f, z = -48f,
                sx = 9f, sy = 1.2f, sz = 8f,
                yawRad = -0.2f,
                colorHex = 0xFFFACC15L,
                secondaryHex = 0xFF111827L,
                isSolidWall = false,
                isRamp = true
            ),
            TrackProp(
                kind = PropKind.TOY_BLOCK,
                x = 58f, y = 0f, z = -88f,
                sx = 14f, sy = 10f, sz = 14f,
                yawRad = 0.3f,
                colorHex = 0xFFDC2626L,
                secondaryHex = 0xFFFACC15L
            ),
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

    val all: List<Track> by lazy {
        listOf(
            studyDesk,
            gamerDesk,
            kitchenCounter,
            artDesk,
            bookshelfSpiral,
            bathtubRim,
            toyBox,
            workshopBench
        )
    }

    val deskCupTracks: List<Track> by lazy {
        listOf(studyDesk, gamerDesk, kitchenCounter, artDesk)
    }
    val roomCupTracks: List<Track> by lazy {
        listOf(bookshelfSpiral, bathtubRim, toyBox, workshopBench)
    }

    fun byId(id: Int): Track = when (id) {
        0 -> studyDesk
        1 -> gamerDesk
        2 -> kitchenCounter
        3 -> artDesk
        4 -> bookshelfSpiral
        5 -> bathtubRim
        6 -> toyBox
        7 -> workshopBench
        else -> studyDesk
    }
}
