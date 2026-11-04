package com.example.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class ItemType(val id: Int, val label: String, val badgeColorHex: Long) {
    NONE(0, "EMPTY", 0xFF475569L),
    MARBLE(1, "MARBLE SHELL", 0xFF22C55EL),
    ERASER(2, "PINK ERASER", 0xFFF43F5EL),
    BATTERY(3, "9V BATTERY", 0xFF38BDF8L),
    TRIPLE_TACKS(4, "TRIPLE TACKS", 0xFFFACC15L),
    PAPER_PLANE(5, "PAPER PLANE", 0xFFA855F7L),
    MAGNET(6, "DESK MAGNET", 0xFFEF4444L);

    val displayName: String get() = label

    companion object {
        fun fromId(id: Int): ItemType = entries.firstOrNull { it.id == id } ?: NONE
    }
}

enum class ProjectileKind(val id: Int) {
    MARBLE(0),
    ERASER(1),
    TACK(2),
    PAPER_PLANE(3),
    MARBLE_HOMING(4);

    companion object {
        fun fromId(id: Int): ProjectileKind = entries.firstOrNull { it.id == id } ?: MARBLE
    }
}

data class ActiveItemBox(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    var active: Boolean = true,
    var respawnTimerSec: Float = 0f,
    var rotationRad: Float = 0f
)

data class ActiveCoin(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    var active: Boolean = true,
    var respawnTimerSec: Float = 0f,
    var rotationRad: Float = 0f
)

data class ActivePylon(
    val propId: Int,
    val modelPath: String,
    val baseX: Float,
    val baseY: Float,
    val baseZ: Float,
    val baseYawRad: Float,
    val scale: Float,
    val radius: Float,
    var x: Float = baseX,
    var y: Float = baseY,
    var z: Float = baseZ,
    var yawRad: Float = baseYawRad,
    var pitchRad: Float = 0f,
    var rollRad: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var omegaPitch: Float = 0f,
    var omegaRoll: Float = 0f,
    var respawnTimerSec: Float = 0f
) {
    val isKnocked: Boolean get() = respawnTimerSec > 0f
}

data class WorldProjectile(
    val id: Int,
    val kind: ProjectileKind,
    val ownerSlot: Int,
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vz: Float,
    var headingRad: Float = 0f,
    var distanceTravelled: Float = 0f,
    var bouncesRemaining: Int = GameConstants.MARBLE_MAX_BOUNCES,
    var lifeRemainingSec: Float = GameConstants.MARBLE_LIFETIME_SEC,
    var targetSlot: Int = -1,
    var trackSampleIdx: Int = -1
)

object ItemsEngine {

    /**
     * v7 rubber-banded item table. Every item can drop in every place (weights shift with the
     * normalised race position, like Mario Kart's distance tables), and the same item is re-rolled
     * once if the kart just had it — previously the leader could only ever see Eraser / Marble /
     * Tacks, which is all a good player ever got.
     */
    fun rollItemForPosition(
        position: Int,
        itemMode: ItemMode,
        random: Random = Random.Default,
        racerCount: Int = GameConstants.MAX_RACERS,
        lastItem: ItemType = ItemType.NONE
    ): ItemType {
        if (itemMode == ItemMode.OFF) return ItemType.NONE
        if (itemMode == ItemMode.SKILL_ONLY) {
            return if (random.nextFloat() < 0.55f) ItemType.BATTERY else ItemType.TRIPLE_TACKS
        }
        val n = racerCount.coerceAtLeast(2)
        val place = ((position - 1).toFloat() / (n - 1)).coerceIn(0f, 1f)
        val table = weightsFor(place, position == 1, itemMode == ItemMode.FRANTIC)
        var pick = pickWeighted(table, random)
        if (pick == lastItem) pick = pickWeighted(table, random)
        return pick
    }

    private val ORDER = arrayOf(ItemType.MARBLE, ItemType.ERASER, ItemType.BATTERY, ItemType.TRIPLE_TACKS, ItemType.PAPER_PLANE, ItemType.MAGNET)

    //                                     MARBLE ERASER BATTERY TACKS PLANE MAGNET
    private val LEADER = floatArrayOf(      26f,   26f,   16f,   20f,   6f,   6f)
    private val FRONT = floatArrayOf(       24f,   18f,   20f,   18f,  10f,  10f)
    private val MID = floatArrayOf(         16f,    8f,   24f,   18f,  16f,  18f)
    private val BACK = floatArrayOf(         8f,    0f,   30f,   10f,  26f,  26f)
    private val FRANTIC_SHIFT = floatArrayOf(-6f,  -6f,    4f,    0f,   6f,   6f)

    internal fun weightsFor(place: Float, isLeader: Boolean, frantic: Boolean): FloatArray {
        val base = when {
            isLeader -> LEADER
            place < 0.34f -> FRONT
            place < 0.67f -> MID
            else -> BACK
        }
        return FloatArray(base.size) { i -> (base[i] + if (frantic) FRANTIC_SHIFT[i] else 0f).coerceAtLeast(0f) }
    }

    private fun pickWeighted(w: FloatArray, random: Random): ItemType {
        val total = w.sum()
        var r = random.nextFloat() * total
        for (i in w.indices) {
            r -= w[i]
            if (r <= 0f && w[i] > 0f) return ORDER[i]
        }
        return ORDER[w.indices.last { w[it] > 0f }]
    }

    private fun keepOnTrack(p: WorldProjectile, track: Track) {
        if (track.samples.isEmpty()) return
        val near = track.findNearestSampleWindowed(p.x, p.y, p.z, p.trackSampleIdx, windowRadius = 24)
        p.trackSampleIdx = near.sample.index
        val f = track.surfaceFrame(near.sample, p.x, p.z)
        val limit = track.roadWidth * 0.5f + 0.4f
        if (abs(f.lateral) > limit) {
            val side = if (f.lateral > 0f) 1f else -1f
            // push back inside and reflect the outward velocity component (elastic-ish, 0.8)
            val excess = f.lateral - side * limit
            p.x -= f.rightX * excess
            p.z -= f.rightZ * excess
            val vOut = (p.vx * f.rightX + p.vz * f.rightZ) * side
            if (vOut > 0f) {
                p.vx -= f.rightX * side * vOut * 1.8f
                p.vz -= f.rightZ * side * vOut * 1.8f
            }
        }
        val targetY = f.y + 0.5f
        p.y += (targetY - p.y) * 0.5f
    }

    fun stepProjectilesAndPickups(
        dt: Float,
        track: Track,
        karts: List<KartRuntime>,
        itemBoxes: List<ActiveItemBox>,
        coins: List<ActiveCoin>,
        projectiles: MutableList<WorldProjectile>,
        itemMode: ItemMode,
        isTimeTrial: Boolean = false,
        hostAuthority: Boolean = true,
        localSlotIndex: Int = 0,
        onHitEvent: (victimSlot: Int, attackerSlot: Int, kind: ProjectileKind) -> Unit,
        onBoxPickedUp: (slot: Int, boxId: Int, grantedItem: ItemType) -> Unit,
        onCoinPickedUp: (slot: Int, coinId: Int) -> Unit = { _, _ -> }
    ) {
        // 1. Update Item Boxes & Check Pickups
        for (box in itemBoxes) {
            box.rotationRad += dt * 2.2f
            if (itemMode == ItemMode.OFF || isTimeTrial) {
                box.active = false
                continue
            }
            if (!box.active) {
                box.respawnTimerSec -= dt
                if (box.respawnTimerSec <= 0f) {
                    box.active = true
                }
                continue
            }
            for (kart in karts) {
                if (!hostAuthority && kart.slotIndex != localSlotIndex) continue
                if (kart.isRespawning) continue
                val dx = kart.x - box.x
                val dz = kart.z - box.z
                if (dx * dx + dz * dz < 3.6f && abs(kart.y - box.y) < 3.0f) {
                    box.active = false
                    box.respawnTimerSec = GameConstants.ITEM_BOX_RESPAWN_SEC
                    if (kart.heldItem == ItemType.NONE && kart.rouletteTimerSec <= 0f) {
                        val rolled = if (hostAuthority) {
                            rollItemForPosition(kart.racePosition, itemMode, racerCount = karts.size, lastItem = kart.lastRolledItem)
                        } else {
                            ItemType.BATTERY // Client optimistic roulette start; host event grants authoritative item
                        }
                        kart.pendingItem = rolled
                        kart.lastRolledItem = rolled
                        kart.rouletteTimerSec = GameConstants.ITEM_ROULETTE_SEC
                        onBoxPickedUp(kart.slotIndex, box.id, rolled)
                    }
                    break
                }
            }
        }

        // 2. Update Coins & Check Pickups
        for (coin in coins) {
            coin.rotationRad += dt * 3.2f
            if (!coin.active) {
                coin.respawnTimerSec -= dt
                if (coin.respawnTimerSec <= 0f) {
                    coin.active = true
                }
                continue
            }
            for (kart in karts) {
                if (kart.isRespawning) continue
                val dx = kart.x - coin.x
                val dz = kart.z - coin.z
                if (dx * dx + dz * dz < 3.2f && abs(kart.y - coin.y) < 2.8f) {
                    coin.active = false
                    coin.respawnTimerSec = GameConstants.COIN_RESPAWN_SEC
                    if (kart.coinsHeld < GameConstants.MAX_COINS_HELD) {
                        kart.coinsHeld++
                    }
                    onCoinPickedUp(kart.slotIndex, coin.id)
                    break
                }
            }
        }

        // 3. Step Active Projectiles
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.lifeRemainingSec -= dt
            if (p.lifeRemainingSec <= 0f) {
                iter.remove()
                continue
            }

            when (p.kind) {
                ProjectileKind.ERASER -> {
                    p.headingRad += dt * 0.5f
                }
                ProjectileKind.MARBLE, ProjectileKind.MARBLE_HOMING, ProjectileKind.TACK -> {
                    if (p.kind == ProjectileKind.MARBLE_HOMING && p.targetSlot >= 0) {
                        val target = karts.firstOrNull { it.slotIndex == p.targetSlot && !it.isRespawning }
                        if (target != null) {
                            val dx = target.x - p.x
                            val dz = target.z - p.z
                            val dist = hypot(dx, dz).coerceAtLeast(0.01f)
                            val speed = hypot(p.vx, p.vz).coerceAtLeast(GameConstants.MARBLE_SPEED)
                            val desiredVx = (dx / dist) * speed
                            val desiredVz = (dz / dist) * speed
                            p.vx += (desiredVx - p.vx) * (3.5f * dt).coerceAtMost(1f)
                            p.vz += (desiredVz - p.vz) * (3.5f * dt).coerceAtMost(1f)
                        }
                    }

                    val nextX = p.x + p.vx * dt
                    val nextZ = p.z + p.vz * dt
                    var bounced = false

                    if (abs(nextX) > track.deskHalfExtentX - 2f) {
                        p.vx = -p.vx
                        bounced = true
                    }
                    if (abs(nextZ) > track.deskHalfExtentZ - 2f) {
                        p.vz = -p.vz
                        bounced = true
                    }

                    // Bounce against solid placed props (landmarks)
                    for (prop in track.placedProps) {
                        if (!prop.solid || prop.isPylon) continue
                        val dx = nextX - prop.x
                        val dz = nextZ - prop.z
                        val d = hypot(dx, dz)
                        val minR = prop.radius + 0.6f
                        if (d < minR && d > 0.001f) {
                            val nx = dx / d
                            val nz = dz / d
                            val dot = p.vx * nx + p.vz * nz
                            if (dot < 0f) {
                                p.vx -= 2f * dot * nx
                                p.vz -= 2f * dot * nz
                            }
                            bounced = true
                            break
                        }
                    }

                    if (bounced) {
                        p.bouncesRemaining--
                        if (p.bouncesRemaining < 0) {
                            iter.remove()
                            continue
                        }
                    }
                    val stepDist = hypot(p.vx, p.vz) * dt
                    p.distanceTravelled += stepDist
                    p.x += p.vx * dt
                    p.z += p.vz * dt
                    // v7: marbles/tacks roll ON the road: follow its height (bridges, slopes) and
                    // bank off the road edges like a kerb wall instead of flying off the track
                    // in a straight line and floating in the air.
                    if (p.kind != ProjectileKind.TACK || p.vx * p.vx + p.vz * p.vz > 1f) {
                        keepOnTrack(p, track)
                    }
                    p.headingRad = atan2(p.vx, p.vz)
                }
                ProjectileKind.PAPER_PLANE -> {
                    val target = karts.firstOrNull { it.slotIndex == p.targetSlot }
                    if (target != null) {
                        val dx = target.x - p.x
                        val dz = target.z - p.z
                        val dist = hypot(dx, dz).coerceAtLeast(0.01f)
                        val desiredHeading = atan2(dx, dz)
                        p.headingRad = desiredHeading
                        val speed = 38f
                        p.vx = (dx / dist) * speed
                        p.vz = (dz / dist) * speed
                        p.x += p.vx * dt
                        p.z += p.vz * dt
                        p.y = target.y + 1.4f + sin(p.lifeRemainingSec * 7f) * 0.35f
                    } else {
                        p.x += p.vx * dt
                        p.z += p.vz * dt
                    }
                }
            }

            // Check collision with karts (Host Authority only — Part B2)
            if (hostAuthority) {
                var hitAnyone = false
                for (kart in karts) {
                    if (kart.isRespawning || kart.invulnerableTimerSec > 0f || kart.ghostTimerSec > 0f) continue
                    if (kart.slotIndex == p.ownerSlot && p.lifeRemainingSec > GameConstants.MARBLE_LIFETIME_SEC - 0.35f) {
                        continue
                    }
                    val dx = kart.x - p.x
                    val dz = kart.z - p.z
                    if (dx * dx + dz * dz < 2.35f && abs(kart.y - p.y) < 2.4f) {
                        if (kart.shieldEraserHeld) {
                            kart.shieldEraserHeld = false
                            kart.heldItem = ItemType.NONE
                            hitAnyone = true
                            break
                        }
                        KartPhysics.triggerSpinOut(kart)
                        onHitEvent(kart.slotIndex, p.ownerSlot, p.kind)
                        hitAnyone = true
                        break
                    }
                }
                if (hitAnyone) {
                    iter.remove()
                }
            }
        }
    }
}
