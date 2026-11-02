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
    const val TILT_DEADZONE_DEG = 3.0f
    const val TILT_FULL_LOCK_DEG = 22.0f

    // Hop, Gravity, Ramps & Tricks
    const val GRAVITY = 30f                   // 30 u/s^2
    const val HOP_DURATION_SEC = 0.12f
    const val HOP_VELOCITY_Y = 5.4f
    const val RAMP_LAUNCH_VY = 13.5f
    const val TRICK_BOOST_SEC = 0.50f
    const val FALL_OFF_Y = -5f
    const val LAKITU_RESPAWN_SEC = 1.00f
    const val GHOST_AFTER_RESPAWN_SEC = 1.50f
    const val PYLON_RESPAWN_SEC = 6.00f       // Part H3: Knockable pylons respawn upright after 6s
    const val PYLON_HIT_SPEED_FACTOR = 0.85f  // Part H3: Knockable pylons slow kart by 15%

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

    // Networking & Remote Interpolation
    const val PROTOCOL_VERSION: Byte = 4
    const val UDP_DISCOVERY_PORT = 47777
    const val TCP_CONTROL_PORT = 47778
    const val UDP_STATE_PORT = 47779
    const val BEACON_INTERVAL_MS = 500L
    const val PROBE_INTERVAL_MS = 1000L
    const val ROOM_EXPIRY_MS = 3000L
    const val HEARTBEAT_INTERVAL_MS = 1000L
    const val PEER_TIMEOUT_MS = 5000L
    const val REJOIN_WINDOW_MS = 30_000L
    const val INTERP_BUFFER_MS = 100L
    const val MAX_EXTRAPOLATION_MS = 150L
    const val TELEPORT_ERROR_THRESHOLD = 8.0f

    // Grand Prix Points for 8 positions
    val GRAND_PRIX_POINTS = intArrayOf(15, 12, 10, 8, 6, 4, 2, 1)

    // Palette of Kart Swatches (12 core + 4 bonus)
    val KART_COLORS = longArrayOf(
        0xFFFF3D7FL, // Hot Pink
        0xFFFFB547L, // Lamp Amber
        0xFF2DE2E6L, // Boost Cyan
        0xFFA3E635L, // Success Lime
        0xFFFB7185L, // Danger Coral
        0xFFA855F7L, // Hyper Purple
        0xFF3B82F6L, // Cobalt Blue
        0xFFF8FAFCL, // Chalk White
        0xFFEF4444L, // Racing Red
        0xFF10B981L, // Mint Emerald
        0xFFFACC15L, // Solar Gold
        0xFFF97316L, // Tangerine
        0xFFEC4899L, // Magenta Neon
        0xFF06B6D4L, // Aqua Marine
        0xFF8B5CF6L, // Electric Violet
        0xFF94A3B8L  // Titanium Silver
    )

    val KART_COLOR_NAMES = arrayOf(
        "Hot Pink", "Lamp Amber", "Boost Cyan", "Lime",
        "Coral", "Purple", "Cobalt", "Chalk",
        "Crimson", "Emerald", "Gold", "Tangerine",
        "Magenta", "Aqua", "Violet", "Silver"
    )

    val PAINT_FINISH_NAMES = arrayOf("Gloss", "Matte", "Metallic", "Pearl")

    val ROTATING_LOADING_TIPS = arrayOf(
        "Hold DRIFT through a corner until sparks turn purple for an Ultra Mini-Turbo!",
        "Tap gas in the 0.35s window right after '2' fades for a Rocket Start!",
        "Collect up to 10 gold coins on the racing line for +6% extra top speed!",
        "Traffic cones & chess pawns are knockable pylons — clip the apex with only 15% slowdown!",
        "Tap DRIFT right at the lip of a bridge or ramp to pull off a Trick Boost!",
        "Host Shares the Party: any Track Pack owned by the host is playable by the whole LAN lobby!"
    )
}

enum class KartBodyType(
    val id: Int,
    val title: String,
    val subtitle: String,
    val drmAssetPath: String,
    val isDrm2: Boolean = false,
    val mass: Float,
    val topSpeedFactor: Float,
    val accelFactor: Float,
    val handlingFactor: Float,
    val statSpeed: Int = 3,
    val statAccel: Int = 3,
    val statHandling: Int = 3,
    val statWeight: Int = 3,
    val unlockCoins: Int = 0
) {
    CLASSIC(
        id = 0,
        title = "Apex GT Truck",
        subtitle = "Balanced street pickup racer",
        drmAssetPath = "models/kart_truck_red.drm",
        mass = 1.00f,
        topSpeedFactor = 1.00f,
        accelFactor = 1.00f,
        handlingFactor = 1.00f,
        statSpeed = 3,
        statAccel = 3,
        statHandling = 3,
        statWeight = 3
    ),
    PENCIL_ROCKET(
        id = 1,
        title = "Moto Striker",
        subtitle = "Lightweight superbike — fast accel & tight cornering",
        drmAssetPath = "models/kart_motorcycle.drm",
        mass = 0.78f,
        topSpeedFactor = 0.98f,
        accelFactor = 1.14f,
        handlingFactor = 1.12f,
        statSpeed = 3,
        statAccel = 5,
        statHandling = 5,
        statWeight = 1
    ),
    ERASER_TANK(
        id = 2,
        title = "Goliath 4x4",
        subtitle = "Heavy off-road rig — high top speed & bumper weight",
        drmAssetPath = "models/kart_truck_green.drm",
        mass = 1.32f,
        topSpeedFactor = 1.04f,
        accelFactor = 0.90f,
        handlingFactor = 0.92f,
        statSpeed = 5,
        statAccel = 2,
        statHandling = 2,
        statWeight = 5
    ),
    TURBO_COUPE(
        id = 3,
        title = "Phantom Coupe",
        subtitle = "Low-slung drift machine — rapid mini-turbo charge",
        drmAssetPath = "models/kart_truck_purple.drm",
        mass = 0.94f,
        topSpeedFactor = 1.01f,
        accelFactor = 1.04f,
        handlingFactor = 1.06f,
        statSpeed = 4,
        statAccel = 4,
        statHandling = 4,
        statWeight = 2
    ),
    RALLY_HATCH(
        id = 4,
        title = "Sunburst Rally",
        subtitle = "High-grip rally wagon — reduced off-track penalty",
        drmAssetPath = "models/kart_truck_yellow.drm",
        mass = 1.12f,
        topSpeedFactor = 1.02f,
        accelFactor = 0.97f,
        handlingFactor = 1.02f,
        statSpeed = 4,
        statAccel = 3,
        statHandling = 4,
        statWeight = 4
    ),
    // Part B8: 6th body "CLASSIC TOY" (DRM2 models/props/toy_car.drm, length 2.2 u, stats 4/2/3/3)
    CLASSIC_TOY(
        id = 5,
        title = "Classic Toy",
        subtitle = "Vintage die-cast roadster • 3 custom liveries",
        drmAssetPath = "models/props/toy_car.drm",
        isDrm2 = true,
        mass = 1.02f,
        topSpeedFactor = 1.03f,
        accelFactor = 0.93f,
        handlingFactor = 1.00f,
        statSpeed = 4,
        statAccel = 2,
        statHandling = 3,
        statWeight = 3,
        unlockCoins = 600
    ),
    // v6: Kenney Car Kit (CC0) bodies
    FORMULA_RACER(
        id = 6,
        title = "Formula Racer",
        subtitle = "Open-wheel speedster — top speed king, twitchy at the limit",
        drmAssetPath = "models/kart_car_race.drm",
        mass = 0.86f,
        topSpeedFactor = 1.05f,
        accelFactor = 1.06f,
        handlingFactor = 1.04f,
        statSpeed = 5,
        statAccel = 4,
        statHandling = 4,
        statWeight = 2
    ),
    HYPERION_X(
        id = 7,
        title = "Hyperion X",
        subtitle = "Concept hyper-racer — best launch on the desk",
        drmAssetPath = "models/kart_car_race_future.drm",
        mass = 0.84f,
        topSpeedFactor = 1.04f,
        accelFactor = 1.12f,
        handlingFactor = 1.00f,
        statSpeed = 5,
        statAccel = 5,
        statHandling = 3,
        statWeight = 2,
        unlockCoins = 900
    ),
    STREET_SPRINT(
        id = 8,
        title = "Street Sprint",
        subtitle = "Spoilered sport sedan — balanced and grippy",
        drmAssetPath = "models/kart_car_sedan_sports.drm",
        mass = 1.00f,
        topSpeedFactor = 1.02f,
        accelFactor = 1.02f,
        handlingFactor = 1.05f,
        statSpeed = 4,
        statAccel = 4,
        statHandling = 4,
        statWeight = 3
    ),
    HOT_HATCH(
        id = 9,
        title = "Hot Hatch",
        subtitle = "Small and light — corners like it's on rails",
        drmAssetPath = "models/kart_car_hatchback_sports.drm",
        mass = 0.90f,
        topSpeedFactor = 0.99f,
        accelFactor = 1.08f,
        handlingFactor = 1.10f,
        statSpeed = 3,
        statAccel = 5,
        statHandling = 5,
        statWeight = 2,
        unlockCoins = 500
    ),
    INTERCEPTOR(
        id = 10,
        title = "Interceptor",
        subtitle = "Pursuit cruiser — heavy hitter with a strong top end",
        drmAssetPath = "models/kart_car_police.drm",
        mass = 1.22f,
        topSpeedFactor = 1.04f,
        accelFactor = 0.94f,
        handlingFactor = 0.95f,
        statSpeed = 5,
        statAccel = 2,
        statHandling = 3,
        statWeight = 4,
        unlockCoins = 700
    ),
    CAB_RUSH(
        id = 11,
        title = "Cab Rush",
        subtitle = "Yellow cab — tough, forgiving, always on time",
        drmAssetPath = "models/kart_car_taxi.drm",
        mass = 1.12f,
        topSpeedFactor = 1.00f,
        accelFactor = 0.98f,
        handlingFactor = 1.00f,
        statSpeed = 3,
        statAccel = 3,
        statHandling = 4,
        statWeight = 4
    ),
    SUMMIT_SUV(
        id = 12,
        title = "Summit SUV",
        subtitle = "Luxury SUV — shrugs off bumps and items",
        drmAssetPath = "models/kart_car_suv_luxury.drm",
        mass = 1.28f,
        topSpeedFactor = 1.03f,
        accelFactor = 0.92f,
        handlingFactor = 0.94f,
        statSpeed = 4,
        statAccel = 2,
        statHandling = 3,
        statWeight = 5,
        unlockCoins = 800
    );

    companion object {
        fun fromId(id: Int): KartBodyType = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/**
 * Part F1-F4: Control schemes (SMART default for new players, PRO, TILT, BUTTONS).
 */
enum class ControlSchemeType(val id: Int, val title: String, val description: String) {
    SMART(0, "Smart Drive", "Auto-accelerate + Smart Steering • Drag left zone to steer"),
    PRO(1, "Pro Drift", "Manual Gas + Purple Ultra Drifts • Full off-road freedom"),
    TILT(2, "Gyro Tilt", "Calibrated rotation sensor (±25° lock) • Left-side brake"),
    BUTTONS(3, "Arcade Buttons", "Large 88dp ◀ ▶ buttons with smooth 120ms analogue ramp");

    companion object {
        val THUMB_STEER = SMART
        val DPAD_BUTTONS = BUTTONS
        val TILT_STEER = TILT
        fun fromId(id: Int): ControlSchemeType = entries.firstOrNull { it.id == id } ?: SMART
    }
}

enum class GraphicsQuality(val id: Int, val label: String, val renderScale: Float) {
    LOW(0, "Low (80k tri • Blob Shadows)", 0.75f),
    MEDIUM(1, "Medium (150k tri • 1024 Shadows)", 1.0f),
    HIGH(2, "High (250k tri • 2048 PCF + Post FX)", 1.0f);

    companion object {
        val BATTERY_SAVER = LOW
        val BALANCED = MEDIUM
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
        val MASTER = CHAMPION
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
        val CC_200 = CC_150
        fun fromId(id: Int): CcClass = entries.firstOrNull { it.id == id } ?: CC_150
    }
}

enum class GameMode(val id: Int, val title: String, val subtitle: String) {
    GRAND_PRIX(0, "GRAND PRIX", "4-Race Cup Championship • Desk Cup & Room Cup"),
    QUICK_RACE(1, "QUICK RACE", "Pick any of the 8 real-world tracks • Custom bots, laps & CC"),
    TIME_TRIAL(2, "TIME TRIAL", "3-Lap solo attack • Race your 20 Hz translucent Ghost"),
    MULTIPLAYER(3, "MULTIPLAYER LAN", "Host or Join 2–8 friends over Wi-Fi Hotspot"),
    BATTLE_BALLOONS(4, "BATTLE BALLOONS", "3-Balloon Toy Box Arena • Last kart standing wins");

    companion object {
        fun fromId(id: Int): GameMode = entries.firstOrNull { it.id == id } ?: QUICK_RACE
    }
}

enum class CupType(val id: Int, val title: String, val subtitle: String, val trackIds: List<Int>) {
    DESK_CUP(0, "DESK CUP", "Study Desk • Gamer Desk • Kitchen Counter • Art Desk", listOf(0, 1, 2, 3)),
    ROOM_CUP(1, "ROOM CUP", "Bookshelf Spiral • Bathtub Rim • Toy Box • Midnight Office", listOf(4, 5, 6, 7));

    companion object {
        fun fromId(id: Int): CupType = entries.firstOrNull { it.id == id } ?: DESK_CUP
    }
}
