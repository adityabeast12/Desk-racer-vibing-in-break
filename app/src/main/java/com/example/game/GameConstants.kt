package com.example.game

object GameConstants {
    // Simulation & Tick Rates
    const val SIM_HZ = 60
    const val SIM_DT = 1f / 60f
    const val HUD_HZ = 15
    const val NET_STATE_HZ = 30
    const val MAX_RACERS = 8
    const val DEFAULT_LAPS = 3
    const val NUM_CHECKPOINT_GATES = 16
    const val COINS_PER_TRACK = 10
    const val MAX_COINS_HELD = 10
    const val COIN_SPEED_BONUS_PER_COIN = 0.006f // +0.6% top speed per coin (max +6%)
    const val COIN_RESPAWN_SEC = 8.0f

    // Kart Driving Physics
    const val TOP_SPEED = 30f                 // 30 u/s base at 150cc
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
    const val SOAP_LATERAL_DAMPING = 4.2f     // Bathtub Rim soap slippery zone (grip x0.4)
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
    const val LAKITU_RESPAWN_SEC = 1.00f
    const val GHOST_AFTER_RESPAWN_SEC = 1.50f

    // Progress Watchdog (Part B5)
    const val WATCHDOG_WINDOW_SEC = 3.0f
    const val WATCHDOG_MIN_DISTANCE = 6.0f
    const val WATCHDOG_SAMPLE_INTERVAL_SEC = 0.5f
    const val WATCHDOG_RESPAWN_BACK_OFFSET = 10.0f
    const val BOT_WRONG_WAY_RESPAWN_SEC = 4.0f
    const val FAR_OFF_TRACK_RESPAWN_SEC = 2.5f
    const val FAR_OFF_TRACK_MARGIN = 18.0f

    // Race End Rules (Part B4)
    const val DNF_COUNTDOWN_AFTER_FIRST_FINISH_SEC = 30.0f
    const val HARD_TIME_CAP_PER_LAP_SEC = 120.0f
    const val PRE_RACE_FLYBY_SEC = 4.0f

    // Drift Charge & Spark Tiers (60 Hz frames)
    const val DRIFT_CHARGE_INTO = 5           // +5 units/frame steering into drift
    const val DRIFT_CHARGE_COUNTER = 2        // +2 units/frame counter-steering
    const val SPARK_BLUE_UNITS = 135          // Mini-Turbo
    const val SPARK_ORANGE_UNITS = 270        // Super Mini-Turbo
    const val SPARK_PURPLE_UNITS = 450        // Ultra Mini-Turbo (disabled with Smart Steering)
    const val BOOST_MINI_SEC = 0.62f
    const val BOOST_SUPER_SEC = 1.67f
    const val BOOST_ULTRA_SEC = 2.63f

    // Rocket Start & Spin-Out (Part D2)
    const val ROCKET_START_BOOST_SEC = 1.0f
    const val ROCKET_START_FAIL_SPIN_SEC = 0.60f // 0.6s stall + puff of smoke
    const val SPIN_OUT_DURATION_SEC = 1.10f
    const val SPIN_OUT_SPEED_FACTOR = 0.30f
    const val INVULNERABLE_AFTER_HIT_SEC = 1.50f

    // Items
    const val ITEM_BOX_RESPAWN_SEC = 3.0f
    const val ITEM_ROULETTE_SEC = 1.20f
    const val MARBLE_SPEED = 45f              // 45 u/s
    const val MARBLE_MAX_BOUNCES = 3
    const val MARBLE_LIFETIME_SEC = 4.0f
    const val BATTERY_BOOST_SEC = 1.50f
    const val PAPER_PLANE_FLIGHT_SEC = 3.0f
    const val MAGNET_DURATION_SEC = 3.0f
    const val MAGNET_PULL_ACCEL = 16f

    // Networking & Remote Interpolation (Part B9)
    const val PROTOCOL_VERSION: Byte = 3
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
    const val TELEPORT_ERROR_THRESHOLD = 8.0f

    // Grand Prix Points for 8 positions
    val GRAND_PRIX_POINTS = intArrayOf(15, 12, 10, 8, 6, 4, 2, 1)

    // Palette of 8 Kart Swatches
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

    val ROTATING_LOADING_TIPS = arrayOf(
        "Hold DRIFT through a corner until sparks turn purple for an Ultra Mini-Turbo!",
        "Tap gas in the 0.35s window right after '2' fades for a Rocket Start!",
        "Collect up to 10 gold coins on the racing line for +6% extra top speed!",
        "Hold the ITEM button with a Pink Eraser to trail it behind you as a shield!",
        "Tap DRIFT right at the lip of a bridge or ramp to pull off a Trick Boost!",
        "Paint puddles on Art Desk tint your kart and trigger an instant mini-turbo!"
    )
}

enum class KartBodyType(
    val id: Int,
    val title: String,
    val subtitle: String,
    val drmAssetPath: String,
    val mass: Float,
    val topSpeedFactor: Float,
    val accelFactor: Float,
    val handlingFactor: Float
) {
    CLASSIC(
        id = 0,
        title = "Apex GT Truck",
        subtitle = "Balanced street pickup racer",
        drmAssetPath = "models/kart_truck_red.drm",
        mass = 1.00f,
        topSpeedFactor = 1.00f,
        accelFactor = 1.00f,
        handlingFactor = 1.00f
    ),
    PENCIL_ROCKET(
        id = 1,
        title = "Moto Striker",
        subtitle = "Lightweight superbike — fast accel & tight cornering",
        drmAssetPath = "models/kart_motorcycle.drm",
        mass = 0.78f,
        topSpeedFactor = 0.98f,
        accelFactor = 1.14f,
        handlingFactor = 1.12f
    ),
    ERASER_TANK(
        id = 2,
        title = "Goliath 4x4",
        subtitle = "Heavy off-road rig — high top speed & bumper weight",
        drmAssetPath = "models/kart_truck_green.drm",
        mass = 1.32f,
        topSpeedFactor = 1.04f,
        accelFactor = 0.90f,
        handlingFactor = 0.92f
    ),
    TURBO_COUPE(
        id = 3,
        title = "Phantom Coupe",
        subtitle = "Low-slung drift machine — rapid mini-turbo charge",
        drmAssetPath = "models/kart_truck_purple.drm",
        mass = 0.94f,
        topSpeedFactor = 1.01f,
        accelFactor = 1.04f,
        handlingFactor = 1.06f
    ),
    RALLY_HATCH(
        id = 4,
        title = "Sunburst Rally",
        subtitle = "High-grip rally wagon — reduced off-track penalty",
        drmAssetPath = "models/kart_truck_yellow.drm",
        mass = 1.12f,
        topSpeedFactor = 1.02f,
        accelFactor = 0.97f,
        handlingFactor = 1.02f
    );

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
    ROOKIE(0, "Rookie", 0.88f),
    PRO(1, "Pro", 0.97f),
    CHAMPION(2, "Champion", 1.04f),
    MIXED(3, "Mixed", 0.96f);

    companion object {
        val EASY = ROOKIE
        val NORMAL = PRO
        val HARD = CHAMPION
        fun fromId(id: Int): CpuDifficulty = entries.firstOrNull { it.id == id } ?: PRO
    }
}

enum class ItemMode(val id: Int, val label: String) {
    NORMAL(0, "Normal"),
    FRANTIC(1, "Frantic"),
    SKILL_ONLY(2, "Skill Only"),
    OFF(3, "No Items");

    companion object {
        fun fromId(id: Int): ItemMode = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

enum class CcClass(val id: Int, val label: String, val speedScale: Float) {
    CC_50(0, "50cc", 0.80f),
    CC_100(1, "100cc", 0.90f),
    CC_150(2, "150cc", 1.00f);

    companion object {
        fun fromId(id: Int): CcClass = entries.firstOrNull { it.id == id } ?: CC_150
    }
}

enum class GameMode(val id: Int, val title: String, val subtitle: String) {
    GRAND_PRIX(0, "GRAND PRIX", "4-Race Cup Championship • Desk Cup & Room Cup"),
    QUICK_RACE(1, "QUICK RACE", "Pick any of the 8 tracks • Custom bots, laps & CC"),
    TIME_TRIAL(2, "TIME TRIAL", "3-Lap solo attack • Race your 20 Hz translucent Ghost"),
    MULTIPLAYER(3, "MULTIPLAYER LAN", "Host or Join 2–8 friends over Wi-Fi Hotspot"),
    BATTLE_BALLOONS(4, "BATTLE BALLOONS", "3-Balloon Toy Box Arena • Last kart standing wins");

    companion object {
        fun fromId(id: Int): GameMode = entries.firstOrNull { it.id == id } ?: QUICK_RACE
    }
}

enum class CupType(val id: Int, val title: String, val subtitle: String, val trackIds: List<Int>) {
    DESK_CUP(0, "DESK CUP", "Study Desk • Gamer Desk • Kitchen Counter • Art Desk", listOf(0, 1, 2, 3)),
    ROOM_CUP(1, "ROOM CUP", "Bookshelf Spiral • Bathtub Rim • Toy Box • Workshop Bench", listOf(4, 5, 6, 7));

    companion object {
        fun fromId(id: Int): CupType = entries.firstOrNull { it.id == id } ?: DESK_CUP
    }
}
