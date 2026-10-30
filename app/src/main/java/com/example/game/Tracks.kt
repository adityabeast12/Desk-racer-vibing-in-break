package com.example.game

object Tracks {
    val studyDesk: Track by lazy { buildStudyDeskTrack() }
    val gamerDesk: Track by lazy { buildGamerDeskTrack() }
    val kitchenCounter: Track by lazy { buildKitchenCounterTrack() }

    val all: List<Track> by lazy { listOf(studyDesk, gamerDesk, kitchenCounter) }

    fun byId(id: Int): Track = all.getOrElse(id) { studyDesk }

    /**
     * Track 1: STUDY DESK (Easy)
     * Warm wooden tabletop, stacked textbooks forming chicanes, yellow RULER RAMP jump,
     * coffee MUG roundabout, pencils as rail barriers, sticky notes as boost pads.
     */
    private fun buildStudyDeskTrack(): Track {
        val controlPoints = listOf(
            Vec3(0f, 0f, 45f),      // Start/Finish straight heading North-East
            Vec3(42f, 0f, 42f),     // South-East sweeping turn
            Vec3(62f, 0f, 10f),     // East textbook chicane entry
            Vec3(52f, 0f, -28f),    // Coffee Mug Roundabout curve
            Vec3(18f, 0f, -52f),    // North ruler jump approach
            Vec3(-24f, 0f, -48f),   // Post-jump landing zone
            Vec3(-58f, 0f, -22f),   // West pencil barrier straight
            Vec3(-62f, 0f, 20f),    // South-West hairpin
            Vec3(-32f, 0f, 44f)     // Final sticky-note boost straight
        )

        val props = listOf(
            // Textbooks forming chicanes and desk landmarks
            TrackProp(PropKind.BOOK, x = 22f, y = 0f, z = 14f, sx = 18f, sy = 3.2f, sz = 13f, yawRad = 0.2f, colorHex = 0xFF2563EBL, secondaryHex = 0xFF1D4ED8L),
            TrackProp(PropKind.BOOK, x = -18f, y = 0f, z = -12f, sx = 20f, sy = 3.6f, sz = 14f, yawRad = -0.15f, colorHex = 0xFFDC2626L, secondaryHex = 0xFFB91C1CL),
            TrackProp(PropKind.BOOK, x = -25f, y = 0f, z = 18f, sx = 15f, sy = 2.8f, sz = 12f, yawRad = 0.4f, colorHex = 0xFF16A34AL, secondaryHex = 0xFF15803DL),

            // Yellow Ruler Ramp Jump on the North straight
            TrackProp(
                kind = PropKind.RULER_RAMP,
                x = -2f, y = 0f, z = -50f,
                sx = 12f, sy = 2.4f, sz = 9f,
                yawRad = -1.57f, pitchRad = 0.22f,
                colorHex = 0xFFFACC15L, secondaryHex = 0xFF1E293BL,
                isSolidWall = false, isRamp = true
            ),

            // Giant Coffee Mug Roundabout in North-East corner
            TrackProp(
                kind = PropKind.MUG_ROUNDABOUT,
                x = 40f, y = 0f, z = -22f,
                sx = 7.5f, sy = 7.5f, sz = 7.5f,
                colorHex = 0xFFF8FAFCL, secondaryHex = 0xFF38BDF8L,
                isSolidWall = true, isRoundBumper = true
            ),

            // Pencil Rail Barriers along outer curves
            TrackProp(PropKind.PENCIL_RAIL, x = 0f, y = 0.7f, z = 58f, sx = 44f, sy = 1.3f, sz = 1.3f, yawRad = 0f, colorHex = 0xFFF59E0BL, secondaryHex = 0xFFF43F5EL),
            TrackProp(PropKind.PENCIL_RAIL, x = -74f, y = 0.7f, z = 0f, sx = 42f, sy = 1.3f, sz = 1.3f, yawRad = 1.57f, colorHex = 0xFFF59E0BL, secondaryHex = 0xFFF43F5EL),

            // Sticky Note Boost Pads (Yellow x1.3 boost for 0.8 s)
            TrackProp(
                kind = PropKind.STICKY_BOOST_PAD,
                x = 24f, y = 0.04f, z = 43f,
                sx = 6.5f, sy = 0.08f, sz = 6.5f,
                colorHex = 0xFFFDE047L, secondaryHex = 0xFFF97316L,
                isSolidWall = false, isBoostPad = true
            ),
            TrackProp(
                kind = PropKind.STICKY_BOOST_PAD,
                x = -60f, y = 0.04f, z = -2f,
                sx = 6.5f, sy = 0.08f, sz = 6.5f,
                colorHex = 0xFFFDE047L, secondaryHex = 0xFFF97316L,
                isSolidWall = false, isBoostPad = true
            ),
            TrackProp(
                kind = PropKind.STICKY_BOOST_PAD,
                x = -16f, y = 0.04f, z = 45f,
                sx = 6.5f, sy = 0.08f, sz = 6.5f,
                colorHex = 0xFFFDE047L, secondaryHex = 0xFFF97316L,
                isSolidWall = false, isBoostPad = true
            )
        )

        return Track(
            id = 0,
            name = "STUDY DESK",
            subtitle = "Textbooks, Coffee Mug Roundabout & Yellow Ruler Jump",
            difficultyLabel = "EASY",
            roadWidth = 11.5f,
            deskSurfaceHex = 0xFF8B5A2BL,
            roadSurfaceHex = 0xFFE2E8F0L,
            kerbPrimaryHex = 0xFFEF4444L,
            kerbSecondaryHex = 0xFFF8FAFCL,
            skyTopHex = 0xFF0F172AL,
            skyBottomHex = 0xFF1E293BL,
            fogHex = 0xFF1E293BL,
            controlPoints = controlPoints,
            props = props
        )
    }

    /**
     * Track 2: GAMER DESK (Medium)
     * Dark carbon desk with RGB light strips, giant mechanical KEYBOARD section,
     * mouse-pad straight with boost arrows, and closed laptop crossover ramp.
     */
    private fun buildGamerDeskTrack(): Track {
        val controlPoints = listOf(
            Vec3(0f, 0f, 48f),
            Vec3(48f, 0f, 44f),
            Vec3(66f, 0f, 6f),
            Vec3(34f, 0f, -14f),
            Vec3(52f, 0f, -48f),
            Vec3(0f, 1.8f, -54f),   // Elevated Laptop Bridge section
            Vec3(-52f, 0f, -46f),
            Vec3(-32f, 0f, -10f),
            Vec3(-64f, 0f, 18f),
            Vec3(-42f, 0f, 46f)
        )

        val props = listOf(
            // Giant Mechanical Keyboard near center-south
            TrackProp(
                kind = PropKind.KEYBOARD_BUMP,
                x = 12f, y = 0f, z = 22f,
                sx = 26f, sy = 2.2f, sz = 11f,
                yawRad = 0.1f,
                colorHex = 0xFF0F172AL, secondaryHex = 0xFFA855F7L,
                isSolidWall = true
            ),
            // Closed Aluminum Gaming Laptop Ramp at North section
            TrackProp(
                kind = PropKind.LAPTOP_BRIDGE,
                x = 0f, y = 0f, z = -54f,
                sx = 18f, sy = 2.3f, sz = 11f,
                yawRad = 0f, pitchRad = 0.18f,
                colorHex = 0xFF334155L, secondaryHex = 0xFF00F5D4L,
                isSolidWall = false, isRamp = true
            ),
            // RGB Tower / Speaker Obstacles
            TrackProp(
                kind = PropKind.BOOK,
                x = -14f, y = 0f, z = -26f,
                sx = 12f, sy = 5.5f, sz = 12f,
                colorHex = 0xFF1E1B4BL, secondaryHex = 0xFFEC4899L,
                isSolidWall = true
            ),
            TrackProp(
                kind = PropKind.BOOK,
                x = -22f, y = 0f, z = 24f,
                sx = 14f, sy = 4.2f, sz = 10f,
                colorHex = 0xFF0F172AL, secondaryHex = 0xFF38BDF8L,
                isSolidWall = true
            ),
            // Mouse-pad Neon Boost Pads
            TrackProp(
                kind = PropKind.STICKY_BOOST_PAD,
                x = 58f, y = 0.04f, z = 22f,
                sx = 7f, sy = 0.08f, sz = 7f,
                colorHex = 0xFF00F5D4L, secondaryHex = 0xFFA855F7L,
                isSolidWall = false, isBoostPad = true
            ),
            TrackProp(
                kind = PropKind.STICKY_BOOST_PAD,
                x = -52f, y = 0.04f, z = 34f,
                sx = 7f, sy = 0.08f, sz = 7f,
                colorHex = 0xFF00F5D4L, secondaryHex = 0xFFA855F7L,
                isSolidWall = false, isBoostPad = true
            )
        )

        return Track(
            id = 1,
            name = "GAMER DESK",
            subtitle = "RGB Keyboard Chicane, Mousepad Boosts & Laptop Bridge",
            difficultyLabel = "MEDIUM",
            roadWidth = 10.5f,
            deskSurfaceHex = 0xFF111827L,
            roadSurfaceHex = 0xFF1E293BL,
            kerbPrimaryHex = 0xFF00F5D4L,
            kerbSecondaryHex = 0xFFA855F7L,
            skyTopHex = 0xFF050811L,
            skyBottomHex = 0xFF1E1B4BL,
            fogHex = 0xFF0F172AL,
            controlPoints = controlPoints,
            props = props
        )
    }

    /**
     * Track 3: KITCHEN COUNTER (Hard)
     * Cool marble countertop, wooden cutting-board bridge, round orange bumpers,
     * sink gap jump with fall-off risk, and tight hairpin around a steel kettle.
     */
    private fun buildKitchenCounterTrack(): Track {
        val controlPoints = listOf(
            Vec3(0f, 0f, 46f),
            Vec3(46f, 0f, 42f),
            Vec3(68f, 0f, 12f),
            Vec3(60f, 0f, -32f),    // Tight Kettle Hairpin
            Vec3(22f, 0f, -18f),
            Vec3(6f, 0f, -52f),     // Cutting-board Sink Jump approach
            Vec3(-36f, 0f, -50f),
            Vec3(-66f, 0f, -16f),
            Vec3(-58f, 0f, 28f),
            Vec3(-28f, 0f, 45f)
        )

        val props = listOf(
            // Cutting Board Launch Ramp over the Sink Gap
            TrackProp(
                kind = PropKind.RULER_RAMP,
                x = -12f, y = 0f, z = -51f,
                sx = 13f, sy = 2.4f, sz = 10f,
                yawRad = -1.57f, pitchRad = 0.24f,
                colorHex = 0xFFD97706L, secondaryHex = 0xFFFEF3C7L,
                isSolidWall = false, isRamp = true
            ),
            // Giant Steel Kettle at the North-East Hairpin
            TrackProp(
                kind = PropKind.KETTLE_PILLAR,
                x = 45f, y = 0f, z = -18f,
                sx = 8.5f, sy = 9.0f, sz = 8.5f,
                colorHex = 0xFF94A3B8L, secondaryHex = 0xFF1E293BL,
                isSolidWall = true, isRoundBumper = true
            ),
            // Bouncy Orange Fruit Bumpers along the counter
            TrackProp(
                kind = PropKind.ORANGE_BUMPER,
                x = 26f, y = 0f, z = 34f,
                sx = 4.5f, sy = 4.5f, sz = 4.5f,
                colorHex = 0xFFF97316L, secondaryHex = 0xFF22C55EL,
                isSolidWall = true, isRoundBumper = true
            ),
            TrackProp(
                kind = PropKind.ORANGE_BUMPER,
                x = -46f, y = 0f, z = -4f,
                sx = 4.8f, sy = 4.8f, sz = 4.8f,
                colorHex = 0xFFF97316L, secondaryHex = 0xFF22C55EL,
                isSolidWall = true, isRoundBumper = true
            ),
            TrackProp(
                kind = PropKind.ORANGE_BUMPER,
                x = -20f, y = 0f, z = 24f,
                sx = 5.2f, sy = 5.2f, sz = 5.2f,
                colorHex = 0xFFF97316L, secondaryHex = 0xFF22C55EL,
                isSolidWall = true, isRoundBumper = true
            ),
            // Boost Pads
            TrackProp(
                kind = PropKind.STICKY_BOOST_PAD,
                x = 4f, y = 0.04f, z = -51f,
                sx = 6.5f, sy = 0.08f, sz = 6.5f,
                colorHex = 0xFF38BDF8L, secondaryHex = 0xFFFACC15L,
                isSolidWall = false, isBoostPad = true
            ),
            TrackProp(
                kind = PropKind.STICKY_BOOST_PAD,
                x = -44f, y = 0.04f, z = 38f,
                sx = 6.5f, sy = 0.08f, sz = 6.5f,
                colorHex = 0xFF38BDF8L, secondaryHex = 0xFFFACC15L,
                isSolidWall = false, isBoostPad = true
            )
        )

        return Track(
            id = 2,
            name = "KITCHEN COUNTER",
            subtitle = "Marble Counter, Orange Bumpers, Kettle Hairpin & Sink Gap",
            difficultyLabel = "HARD",
            roadWidth = 9.6f,
            deskSurfaceHex = 0xFFCBD5E1L,
            roadSurfaceHex = 0xFF334155L,
            kerbPrimaryHex = 0xFFF97316L,
            kerbSecondaryHex = 0xFFF8FAFCL,
            skyTopHex = 0xFF0F172AL,
            skyBottomHex = 0xFF334155L,
            fogHex = 0xFF1E293BL,
            hasSinkGapZone = true,
            sinkGapMinX = -27f,
            sinkGapMaxX = -18f,
            sinkGapMinZ = -58f,
            sinkGapMaxZ = -44f,
            controlPoints = controlPoints,
            props = props
        )
    }
}
