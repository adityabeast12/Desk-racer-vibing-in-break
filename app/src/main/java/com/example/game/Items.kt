package com.example.game

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

    companion object {
        fun fromId(id: Int): ItemType = entries.firstOrNull { it.id == id } ?: NONE
    }
}

enum class ProjectileKind(val id: Int) {
    MARBLE(0),
    ERASER(1),
    TACK(2),
    PAPER_PLANE(3);

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
    var bouncesRemaining: Int = GameConstants.MARBLE_MAX_BOUNCES,
    var lifeRemainingSec: Float = GameConstants.MARBLE_LIFETIME_SEC,
    var targetSlot: Int = -1
)

object ItemsEngine {

    /**
     * Rubber-banded item probability table based on race position (1..6) and ItemMode.
     */
    fun rollItemForPosition(position: Int, itemMode: ItemMode, random: Random = Random.Default): ItemType {
        if (itemMode == ItemMode.OFF) return ItemType.NONE
        val r = random.nextFloat()
        if (itemMode == ItemMode.FRANTIC) {
            return when {
                position == 1 -> when {
                    r < 0.35f -> ItemType.MARBLE
                    r < 0.70f -> ItemType.ERASER
                    else -> ItemType.TRIPLE_TACKS
                }
                position in 2..4 -> when {
                    r < 0.25f -> ItemType.BATTERY
                    r < 0.50f -> ItemType.TRIPLE_TACKS
                    r < 0.72f -> ItemType.MARBLE
                    r < 0.86f -> ItemType.MAGNET
                    else -> ItemType.PAPER_PLANE
                }
                else -> when {
                    r < 0.35f -> ItemType.PAPER_PLANE
                    r < 0.70f -> ItemType.MAGNET
                    else -> ItemType.BATTERY
                }
            }
        }

        // Normal Item Mode
        return when (position) {
            1 -> when {
                r < 0.45f -> ItemType.ERASER
                r < 0.82f -> ItemType.MARBLE
                else -> ItemType.TRIPLE_TACKS
            }
            2, 3 -> when {
                r < 0.30f -> ItemType.MARBLE
                r < 0.52f -> ItemType.ERASER
                r < 0.76f -> ItemType.BATTERY
                else -> ItemType.TRIPLE_TACKS
            }
            4 -> when {
                r < 0.35f -> ItemType.BATTERY
                r < 0.60f -> ItemType.TRIPLE_TACKS
                r < 0.80f -> ItemType.MARBLE
                r < 0.92f -> ItemType.MAGNET
                else -> ItemType.PAPER_PLANE
            }
            else -> when {
                // 5th & 6th places: Battery, Paper Plane, Magnet
                r < 0.36f -> ItemType.BATTERY
                r < 0.68f -> ItemType.PAPER_PLANE
                r < 0.92f -> ItemType.MAGNET
                else -> ItemType.TRIPLE_TACKS
            }
        }
    }

    fun stepProjectilesAndPickups(
        dt: Float,
        track: Track,
        karts: List<KartRuntime>,
        itemBoxes: List<ActiveItemBox>,
        projectiles: MutableList<WorldProjectile>,
        itemMode: ItemMode,
        onHitEvent: (victimSlot: Int, attackerSlot: Int, kind: ProjectileKind) -> Unit,
        onBoxPickedUp: (slot: Int, boxId: Int, grantedItem: ItemType) -> Unit
    ) {
        // 1. Update Item Boxes & Check Pickups
        for (box in itemBoxes) {
            box.rotationRad += dt * 2.2f
            if (itemMode == ItemMode.OFF) {
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
                if (kart.isRespawning) continue
                val dx = kart.x - box.x
                val dz = kart.z - box.z
                if (dx * dx + dz * dz < 3.6f) {
                    box.active = false
                    box.respawnTimerSec = GameConstants.ITEM_BOX_RESPAWN_SEC
                    if (kart.heldItem == ItemType.NONE && kart.rouletteTimerSec <= 0f) {
                        val rolled = rollItemForPosition(kart.racePosition, itemMode)
                        kart.pendingItem = rolled
                        kart.rouletteTimerSec = GameConstants.ITEM_ROULETTE_SEC
                        onBoxPickedUp(kart.slotIndex, box.id, rolled)
                    }
                    break
                }
            }
        }

        // 2. Step Active Projectiles
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
                    // Stationary obstacle on the desk
                    p.headingRad += dt * 0.5f
                }
                ProjectileKind.MARBLE, ProjectileKind.TACK -> {
                    val nextX = p.x + p.vx * dt
                    val nextZ = p.z + p.vz * dt
                    var bounced = false

                    // Bounce against desk boundaries
                    if (kotlin.math.abs(nextX) > track.deskHalfExtentX - 2f) {
                        p.vx = -p.vx
                        bounced = true
                    }
                    if (kotlin.math.abs(nextZ) > track.deskHalfExtentZ - 2f) {
                        p.vz = -p.vz
                        bounced = true
                    }

                    // Bounce against solid track props
                    for (prop in track.props) {
                        if (!prop.isSolidWall) continue
                        val hx = prop.sx * 0.5f + 0.6f
                        val hz = prop.sz * 0.5f + 0.6f
                        if (kotlin.math.abs(nextX - prop.x) < hx && kotlin.math.abs(nextZ - prop.z) < hz) {
                            val ox = hx - kotlin.math.abs(nextX - prop.x)
                            val oz = hz - kotlin.math.abs(nextZ - prop.z)
                            if (ox < oz) p.vx = -p.vx else p.vz = -p.vz
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
                    p.x += p.vx * dt
                    p.z += p.vz * dt
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
                        p.y = 1.4f + sin(p.lifeRemainingSec * 7f) * 0.35f
                    } else {
                        p.x += p.vx * dt
                        p.z += p.vz * dt
                    }
                }
            }

            // Check collision with karts
            var hitAnyone = false
            for (kart in karts) {
                if (kart.isRespawning || kart.invulnerableTimerSec > 0f) continue
                // Grace period for the shooter on newly spawned forward projectiles
                if (kart.slotIndex == p.ownerSlot && p.lifeRemainingSec > GameConstants.MARBLE_LIFETIME_SEC - 0.35f) {
                    continue
                }
                val dx = kart.x - p.x
                val dz = kart.z - p.z
                if (dx * dx + dz * dz < 2.35f && kotlin.math.abs(kart.y - p.y) < 2.0f) {
                    // Check if victim is holding an Eraser shield behind or orbiting Tacks
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
