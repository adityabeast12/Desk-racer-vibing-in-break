package com.example.game

import androidx.compose.ui.graphics.Color

enum class PowerUpItem(val displayName: String, val badgeColor: Color) {
    MARBLE_SHELL("MARBLE SHELL", Color(0xFFEF4444)),
    BATTERY_TURBO("BATTERY TURBO", Color(0xFF38BDF8))
}

enum class CameraMode(val label: String) {
    CHASE("CHASE 3D"),
    CLOSE_DRIFT("LOW DRIFT"),
    TOP_DESK("DESK OVERHEAD")
}

data class KartControlInput(
    val left: Boolean = false,
    val right: Boolean = false,
    val gas: Boolean = false,
    val brake: Boolean = false,
    val drift: Boolean = false
)

data class KartState(
    val id: String = "local",
    val name: String = "YOU",
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 18f,
    val vx: Float = 0f,
    val vz: Float = 0f,
    val speed: Float = 0f,
    val rot: Float = 0f,
    val vy: Float = 0f,
    val driftCharge: Int = 0,
    val driftDir: Int = 0, // -1 right, 1 left, 0 none
    val item: PowerUpItem? = null,
    val colorHex: Long = 0xFFFF2A5F,
    val lap: Int = 1,
    val passedNorthCheckpoint: Boolean = false,
    val bestLapMs: Long? = null,
    val currentLapStartMs: Long = System.currentTimeMillis(),
    val boostFrames: Int = 0,
    val spinOutFrames: Int = 0
)

data class BookObstacle(
    val title: String,
    val x: Float,
    val z: Float,
    val width: Float,
    val height: Float,
    val depth: Float,
    val colorHex: Long,
    val spineColorHex: Long
) {
    val rx: Float get() = width / 2f + 0.85f
    val rz: Float get() = depth / 2f + 0.85f
}

data class RulerRamp(
    val x: Float = -8f,
    val y: Float = 0.9f,
    val z: Float = -12f,
    val width: Float = 6f,
    val height: Float = 0.3f,
    val depth: Float = 12f,
    val pitchRad: Float = 0.22f
)

data class ItemBoxEntity(
    val index: Int,
    val x: Float,
    val y: Float = 1.2f,
    val z: Float,
    val visible: Boolean = true,
    val respawnAtMs: Long = 0L,
    val rotX: Float = 0f,
    val rotY: Float = 0f
)

data class MarbleProjectile(
    val id: String,
    val ownerId: String,
    val x: Float,
    val y: Float = 0.45f,
    val z: Float,
    val vx: Float,
    val vz: Float,
    val life: Int = 180,
    val colorHex: Long = 0xFF38BDF8
)

data class SparkParticle(
    val x: Float,
    val y: Float,
    val z: Float,
    val vx: Float,
    val vy: Float,
    val vz: Float,
    val colorHex: Long,
    val life: Int
)

enum class LanRole {
    STANDALONE,
    HOSTING_HOTSPOT,
    JOINED_CLIENT
}

data class DiscoveredHost(
    val roomCode: String,
    val ipAddress: String,
    val tcpPort: Int = 7777,
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class BlueprintSection(
    val partNumber: String,
    val title: String,
    val subtitle: String,
    val keyPoints: List<String>,
    val codeTitle: String,
    val codeSnippet: String
)

object BlueprintArchiveData {
    val defaultBooks = listOf(
        BookObstacle(
            title = "PHYSICS VOL. 1",
            x = 0f,
            z = -15f,
            width = 16f,
            height = 2.0f,
            depth = 12f,
            colorHex = 0xFF2563EB,
            spineColorHex = 0xFF1D4ED8
        ),
        BookObstacle(
            title = "ALGORITHMS",
            x = 14f,
            z = 5f,
            width = 8f,
            height = 2.5f,
            depth = 14f,
            colorHex = 0xFFDC2626,
            spineColorHex = 0xFFB91C1C
        ),
        BookObstacle(
            title = "CIRCUITS HANDBOOK",
            x = -16f,
            z = 8f,
            width = 10f,
            height = 2.0f,
            depth = 10f,
            colorHex = 0xFF16A34A,
            spineColorHex = 0xFF15803D
        )
    )

    val sections = listOf(
        BlueprintSection(
            partNumber = "PART 1",
            title = "Rolling Sphere Kart Physics Trick",
            subtitle = "Why Mario Kart & Micro Machines never use 4-wheel raycast suspension",
            keyPoints = listOf(
                "Realistic 4-wheel raycast suspension flips too easily on desk obstacles and feels sluggish.",
                "The physics collider is a single invisible sphere (RigidBody mass=2, linearDamping=1.5).",
                "Forward/backward arcade impulses drive the sphere while the 3D Kart Mesh rotates independently with hop-drift visual tilt (+0.25 / -0.25 rad)."
            ),
            codeTitle = "Rolling Sphere + Visual Drift Sync",
            codeSnippet = """
// Forward vector based on current steering angle
const forward = new THREE.Vector3(0, 0, -1)
  .applyAxisAngle(new THREE.Vector3(0, 1, 0), newRotY);

// Sync visual kart mesh to the physics sphere
meshRef.current.position.set(pos.x, pos.y - 0.4, pos.z);
meshRef.current.rotation.y = kart.rot + (
  input.drift ? (input.left ? 0.25 : input.right ? -0.25 : 0) : 0
);
            """.trimIndent()
        ),
        BlueprintSection(
            partNumber = "PART 2",
            title = "Zero-Internet Wi-Fi Hotspot LAN Protocol",
            subtitle = "UDP Broadcast Auto-Discovery (8888) + TCP State Sync (7777)",
            keyPoints = listOf(
                "When Phone A turns on Android Wi-Fi Hotspot, Android acts as a local router (usually 192.168.43.1 or 192.168.137.1).",
                "Host APK broadcasts 'DESK_RACER_HOST_HERE' to 255.255.255.255:8888 every second.",
                "Friend's phone listens on UDP port 8888, grabs the sender's IP automatically, and connects via TCP port 7777."
            ),
            codeTitle = "UDP 8888 Auto-Discovery Packet",
            codeSnippet = """
// Host broadcasts every 1000ms on UDP 255.255.255.255:8888
val payload = "DESK_RACER_HOST_HERE|room=KART1|port=7777"
val packet = DatagramPacket(
    bytes, bytes.size,
    InetAddress.getByName("255.255.255.255"), 8888
)
udpSocket.broadcast = true
udpSocket.send(packet)
            """.trimIndent()
        ),
        BlueprintSection(
            partNumber = "PART 3",
            title = "3D Wooden Desk Arena & Obstacles",
            subtitle = "80x80 Wooden Desk, 3 Textbooks, Yellow Ruler Jump Ramp & 6 Octahedron Item Boxes",
            keyPoints = listOf(
                "Desk surface: 80x2x80 wooden tabletop (#8B5A2B) with out-of-bounds respawn at |x| > 40 or |z| > 40.",
                "3 Textbook Obstacles: Blue Book (0, -15), Red Book (14, 5), Green Book (-16, 8) with bounce-back collision.",
                "Yellow Ruler Jump Ramp at (-8, 0.9, -12) with 0.22 rad incline launching karts into the air (vy = 0.28)."
            ),
            codeTitle = "Desk Obstacles & Yellow Ruler Ramp",
            codeSnippet = """
addBook(0, -15, 16, 2, 12, 0x2563eb);
addBook(14, 5, 8, 2.5, 14, 0xdc2626);
addBook(-16, 8, 10, 2, 10, 0x16a34a);

// Yellow Ruler Jump Ramp
ramp.position.set(-8, 0.9, -12);
ramp.rotation.x = 0.22;
if (kart.x > -11 && kart.x < -5 && kart.z > -18 && kart.z < -6 && kart.speed > 0.3) {
  kart.vy = 0.28;
}
            """.trimIndent()
        ),
        BlueprintSection(
            partNumber = "PART 4",
            title = "Hop-Drift Spark Boost & Item Roulette",
            subtitle = "Charge Ultra Sparks (>35 frames) & Fire Bouncy Marbles or Battery Turbo",
            keyPoints = listOf(
                "Tapping DRIFT triggers an arcade hop (vy = 0.22) and tighter turning radius (0.055 vs 0.038 rad/frame).",
                "Holding DRIFT + GAS + Steering charges driftCharge; releasing after 35 frames triggers ULTRA SPARK (speed = 0.95).",
                "6 rotating Cyan Octahedron Item Boxes grant MARBLE SHELL (bouncing projectile) or BATTERY TURBO."
            ),
            codeTitle = "Drift Charge & Item Box Loop",
            codeSnippet = """
if (input.drift && input.gas && (input.left || input.right)) {
  kart.driftCharge++;
  status = kart.driftCharge > 35 ? '🔥 ULTRA SPARK READY!' : '⚡ CHARGING DRIFT...';
}
// On Drift Release:
if (kart.driftCharge > 35) kart.speed = 0.95;
            """.trimIndent()
        )
    )
}
