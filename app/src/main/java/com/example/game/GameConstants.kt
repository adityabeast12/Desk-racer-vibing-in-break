package com.example.game

object GameConstants {
    // Simulation & Tick Rates
    const val SIM_HZ = 60
    const val SIM_DT = 1f / 60f
    const val HUD_HZ = 15
    const val NET_STATE_HZ = 30
    const val MAX_RACERS = 6

    // Kart Driving Physics (Section 4)
    const val TOP_SPEED = 30f                 // 30 u/s
    const val ACCEL = 30f / 2.2f              // 0 to 30 u/s in ~2.2 s (13.636 u/s^2)
    const val COAST_DECEL = 9.5f
    const val BRAKE_DECEL = 26f
    const val REVERSE_MAX_SPEED = -8f         // -8 u/s
    const val OFF_TRACK_SPEED_MULT = 0.55f    // x0.55 on paper/off-track surface
    const val BOOST_SPEED_MULT = 1.35f        // x1.35 on boost
    const val STICKY_NOTE_BOOST_MULT = 1.30f  // x1.30 for 0.8 s
    const val STICKY_NOTE_BOOST_SEC = 0.80f

    // Grip & Steering
    const val NORMAL_LATERAL_DAMPING = 14f
    const val DRIFT_LATERAL_DAMPING = 3.2f
    const val BASE_STEER_RATE = 2.35f         // rad/s at low speed
    const val HIGH_SPEED_STEER_FACTOR = 0.58f
    const val DRIFT_STEER_BONUS = 1.25f
    const val SMART_STEERING_PULL = 2.8f

    // Hop, Gravity, Ramps & Tricks
    const val GRAVITY = 30f                   // 30 u/s^2
    const val HOP_DURATION_SEC = 0.12f
    const val HOP_VELOCITY_Y = 5.4f
    const val RAMP_LAUNCH_VY = 13.5f
    const val TRICK_BOOST_SEC = 0.50f
    const val FALL_OFF_Y = -5f
    const val LAKITU_RESPAWN_SEC = 1.20f
    const val WALL_STUCK_SPEED = 1.0f
    const val WALL_STUCK_SEC = 2.0f

    // Drift Charge & Spark Tiers (60 Hz frames)
    const val DRIFT_CHARGE_INTO = 5           // +5 units/frame steering into drift
    const val DRIFT_CHARGE_COUNTER = 2        // +2 units/frame counter-steering
    const val SPARK_BLUE_UNITS = 135          // Mini-Turbo
    const val SPARK_ORANGE_UNITS = 270        // Super Mini-Turbo
    const val SPARK_PURPLE_UNITS = 450        // Ultra Mini-Turbo (disabled with Smart Steering)
    const val BOOST_MINI_SEC = 0.62f
    const val BOOST_SUPER_SEC = 1.67f
    const val BOOST_ULTRA_SEC = 2.63f

    // Rocket Start & Spin-Out
    const val ROCKET_START_BOOST_SEC = 1.0f
    const val ROCKET_START_FAIL_SPIN_SEC = 0.60f
    const val SPIN_OUT_DURATION_SEC = 1.10f
    const val SPIN_OUT_SPEED_FACTOR = 0.30f
    const val INVULNERABLE_AFTER_HIT_SEC = 1.50f

    // Items (Section 5)
    const val ITEM_BOX_RESPAWN_SEC = 3.0f
    const val ITEM_ROULETTE_SEC = 1.20f
    const val MARBLE_SPEED = 45f              // 45 u/s
    const val MARBLE_MAX_BOUNCES = 3
    const val MARBLE_LIFETIME_SEC = 4.0f
    const val BATTERY_BOOST_SEC = 1.50f
    const val PAPER_PLANE_FLIGHT_SEC = 3.0f
    const val MAGNET_DURATION_SEC = 3.0f
    const val MAGNET_PULL_ACCEL = 16f

    // Networking (Section 7)
    const val PROTOCOL_VERSION: Byte = 2
    const val UDP_DISCOVERY_PORT = 47777
    const val TCP_CONTROL_PORT = 47778
    const val UDP_STATE_PORT = 47779
    const val BEACON_INTERVAL_MS = 500L
    const val PROBE_INTERVAL_MS = 1000L
    const val ROOM_EXPIRY_MS = 3000L
    const val HEARTBEAT_INTERVAL_MS = 1000L
    const val PEER_TIMEOUT_MS = 5000L
    const val INTERP_BUFFER_MS = 100L
    const val MAX_EXTRAPOLATION_MS = 150L
    const val TELEPORT_ERROR_THRESHOLD = 6.0f

    // Palette of 8 Kart Swatches & 3 Kart Bodies
    val KART_COLORS = longArrayOf(
        0xFFFF2A5FL, // Crimson Red
        0xFF00F5D4L, // Rival Teal
        0xFF38BDF8L, // Sky Cyan
        0xFFFACC15L, // Ruler Yellow
        0xFF22C55EL, // Nitro Green
        0xFFA855F7L, // Hyper Purple
        0xFFF97316L, // Blaze Orange
        0xFFF8FAFCL  // Chalk White
    )

    val KART_COLOR_NAMES = arrayOf(
        "Crimson", "Teal", "Sky", "Yellow", "Emerald", "Purple", "Blaze", "Chalk"
    )
}

enum class KartBodyType(val id: Int, val title: String, val subtitle: String) {
    CLASSIC(0, "Classic Kart", "Balanced twin-pipe micro racer"),
    PENCIL_ROCKET(1, "Pencil Rocket", "Hexagonal graphite fuselage & eraser tail"),
    ERASER_TANK(2, "Eraser Tank", "Wide dual-rubber block cruiser");

    companion object {
        fun fromId(id: Int): KartBodyType = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

enum class ControlSchemeType(val id: Int, val title: String, val description: String) {
    THUMB_STEER(0, "Thumb Steer Zone", "Left 40% screen virtual thumb-drag steering"),
    DPAD_BUTTONS(1, "Left / Right Buttons", "Classic arcade directional buttons on left"),
    TILT_STEER(2, "Gyro Tilt Steer", "Steer by tilting phone in landscape");

    companion object {
        fun fromId(id: Int): ControlSchemeType = entries.firstOrNull { it.id == id } ?: THUMB_STEER
    }
}

enum class GraphicsQuality(val id: Int, val label: String, val renderScale: Float) {
    LOW(0, "Low (Battery 30 FPS)", 0.75f),
    MEDIUM(1, "Medium (60 FPS)", 1.0f),
    HIGH(2, "High (60 FPS + 4x MSAA)", 1.0f);

    companion object {
        fun fromId(id: Int): GraphicsQuality = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

enum class CpuDifficulty(val id: Int, val label: String, val baseSpeedMult: Float) {
    EASY(0, "Easy", 0.88f),
    NORMAL(1, "Normal", 0.96f),
    HARD(2, "Hard", 1.03f);

    companion object {
        fun fromId(id: Int): CpuDifficulty = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

enum class ItemMode(val id: Int, val label: String) {
    NORMAL(0, "Normal"),
    FRANTIC(1, "Frantic"),
    OFF(2, "No Items");

    companion object {
        fun fromId(id: Int): ItemMode = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}
