package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Part E1: Reserved percentage zones of the safe drawing rect.
 * By mathematical construction, none of the 8 peripheral zones intersect each other!
 */
enum class HudZone(
    val leftFrac: Float,
    val topFrac: Float,
    val rightFrac: Float,
    val bottomFrac: Float,
    val align: Alignment
) {
    TOP_LEFT(0.00f, 0.00f, 0.28f, 0.22f, Alignment.TopStart),
    TOP_CENTER(0.34f, 0.00f, 0.66f, 0.14f, Alignment.TopCenter),
    TOP_RIGHT(0.72f, 0.00f, 1.00f, 0.22f, Alignment.TopEnd),
    LEFT_MID(0.00f, 0.26f, 0.18f, 0.56f, Alignment.CenterStart),
    RIGHT_MID(0.84f, 0.26f, 1.00f, 0.56f, Alignment.CenterEnd),
    BOTTOM_LEFT(0.00f, 0.58f, 0.40f, 1.00f, Alignment.BottomStart),
    BOTTOM_CENTER(0.42f, 0.78f, 0.58f, 1.00f, Alignment.BottomCenter),
    BOTTOM_RIGHT(0.60f, 0.58f, 1.00f, 1.00f, Alignment.BottomEnd),
    CENTER(0.25f, 0.22f, 0.75f, 0.70f, Alignment.Center)
}

private class HudZoneParentData(val zone: HudZone) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@HudZoneParentData
}

fun Modifier.hudZone(zone: HudZone): Modifier = this.then(HudZoneParentData(zone))

data class HudDebugChildBound(
    val zone: HudZone,
    val rect: IntRect
)

/**
 * Part E1 & E5: Custom HUD Layout Engine that measures and places each child strictly inside its
 * reserved percentage zone of the safe rect. If a child's intrinsic content is larger than its
 * zone, it scales down via graphicsLayer (down to 0.75x) and never spills outside the zone.
 * Below 600 dp width, RIGHT_MID ladder is hidden and LEFT_MID minimap is capped at 96 dp.
 * Below 640 dp width, BOTTOM_CENTER speedometer shrinks to fit cleanly.
 */
@Composable
fun HudLayout(
    modifier: Modifier = Modifier,
    applySafeInsets: Boolean = true,
    showDebugZones: Boolean = false,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val debugPlacedBounds = remember { mutableStateListOf<HudDebugChildBound>() }

    val insetModifier = if (applySafeInsets) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(insetModifier)
    ) {
        Layout(
            content = content,
            modifier = Modifier.fillMaxSize()
        ) { measurables, constraints ->
            val safeW = constraints.maxWidth.coerceAtLeast(1)
            val safeH = constraints.maxHeight.coerceAtLeast(1)
            val widthDp = with(density) { safeW.toDp() }

            val isNarrow600 = widthDp < 600.dp
            val isNarrow640 = widthDp < 640.dp
            val minimapCapPx = with(density) { (if (isNarrow600) 96.dp else 132.dp).roundToPx() }

            val placeablesWithZone = measurables.map { measurable ->
                val zoneData = measurable.parentData as? HudZoneParentData
                val zone = zoneData?.zone ?: HudZone.CENTER

                val zoneLeft = (safeW * zone.leftFrac).roundToInt()
                val zoneTop = (safeH * zone.topFrac).roundToInt()
                val zoneRight = (safeW * zone.rightFrac).roundToInt()
                val zoneBottom = (safeH * zone.bottomFrac).roundToInt()
                var zoneW = (zoneRight - zoneLeft).coerceAtLeast(0)
                var zoneH = (zoneBottom - zoneTop).coerceAtLeast(0)

                // Below 600 dp width, hide RIGHT_MID ladder and cap LEFT_MID minimap to 96 dp (Part E1)
                if (zone == HudZone.RIGHT_MID && isNarrow600) {
                    zoneW = 0
                    zoneH = 0
                } else if (zone == HudZone.LEFT_MID) {
                    zoneW = min(zoneW, minimapCapPx)
                    zoneH = min(zoneH, minimapCapPx)
                }

                // Allow up to 1.33x zone size during measure so we can compute a clean scaleDown >= 0.75f if needed
                val measureMaxW = if (zoneW == 0) 0 else ((zoneW / 0.75f).roundToInt()).coerceAtLeast(zoneW)
                val measureMaxH = if (zoneH == 0) 0 else ((zoneH / 0.75f).roundToInt()).coerceAtLeast(zoneH)

                val placeable = measurable.measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = if (zone == HudZone.BOTTOM_LEFT || zone == HudZone.BOTTOM_RIGHT) zoneW else measureMaxW,
                        minHeight = 0,
                        maxHeight = if (zone == HudZone.BOTTOM_LEFT || zone == HudZone.BOTTOM_RIGHT) zoneH else measureMaxH
                    )
                )

                val scaleFactor = if (zoneW > 0 && zoneH > 0 && placeable.width > 0 && placeable.height > 0) {
                    val sx = zoneW.toFloat() / placeable.width.toFloat()
                    val sy = zoneH.toFloat() / placeable.height.toFloat()
                    val rawScale = min(1f, min(sx, sy))
                    val extraShrink = if (zone == HudZone.BOTTOM_CENTER && isNarrow640) min(rawScale, 0.85f) else rawScale
                    extraShrink.coerceIn(0.75f, 1.0f)
                } else {
                    1.0f
                }

                val effW = (placeable.width * scaleFactor).roundToInt().coerceAtMost(zoneW)
                val effH = (placeable.height * scaleFactor).roundToInt().coerceAtMost(zoneH)

                val alignedOffset = zone.align.align(
                    size = androidx.compose.ui.unit.IntSize(effW, effH),
                    space = androidx.compose.ui.unit.IntSize(zoneW, zoneH),
                    layoutDirection = layoutDirection
                )

                val finalX = (zoneLeft + alignedOffset.x).coerceIn(zoneLeft, (zoneRight - effW).coerceAtLeast(zoneLeft))
                val finalY = (zoneTop + alignedOffset.y).coerceIn(zoneTop, (zoneBottom - effH).coerceAtLeast(zoneTop))

                // Since placeWithLayer scales around the center of the placeable by default,
                // adjust placement offset so the scaled visual box sits at (finalX, finalY)
                val layerOffsetX = finalX - ((placeable.width - effW) / 2)
                val layerOffsetY = finalY - ((placeable.height - effH) / 2)

                val bound = IntRect(
                    left = finalX,
                    top = finalY,
                    right = finalX + effW,
                    bottom = finalY + effH
                )

                Triple(placeable, Triple(layerOffsetX, layerOffsetY, scaleFactor), HudDebugChildBound(zone, bound))
            }

            if (showDebugZones) {
                debugPlacedBounds.clear()
                for (item in placeablesWithZone) {
                    if (item.third.rect.width > 0 && item.third.rect.height > 0) {
                        debugPlacedBounds.add(item.third)
                    }
                }
            }

            layout(safeW, safeH) {
                for ((placeable, placement, debugInfo) in placeablesWithZone) {
                    if (debugInfo.rect.width <= 0 || debugInfo.rect.height <= 0) continue
                    val (lx, ly, scale) = placement
                    placeable.placeWithLayer(lx, ly) {
                        scaleX = scale
                        scaleY = scale
                    }
                }
            }
        }

        // Part E5: Debug Overlay showing reserved HUD zone rects and highlighting any intersecting sibling bounds in red
        if (showDebugZones) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                for (zone in HudZone.entries) {
                    val left = w * zone.leftFrac
                    val top = h * zone.topFrac
                    val zw = w * (zone.rightFrac - zone.leftFrac)
                    val zh = h * (zone.bottomFrac - zone.topFrac)
                    drawRect(
                        color = Color(0x442DE2E6),
                        topLeft = Offset(left, top),
                        size = Size(zw, zh),
                        style = Stroke(width = 1.5f)
                    )
                }

                val bounds = debugPlacedBounds.toList()
                for (i in bounds.indices) {
                    val b1 = bounds[i].rect
                    var intersectsSibling = false
                    for (j in bounds.indices) {
                        if (i == j) continue
                        val b2 = bounds[j].rect
                        val overlap = b1.left < b2.right &&
                            b1.right > b2.left &&
                            b1.top < b2.bottom &&
                            b1.bottom > b2.top
                        if (overlap) {
                            intersectsSibling = true
                            break
                        }
                    }
                    val strokeColor = if (intersectsSibling) Color(0xFFFF1744) else Color(0xFFA3E635)
                    drawRect(
                        color = strokeColor,
                        topLeft = Offset(b1.left.toFloat(), b1.top.toFloat()),
                        size = Size(b1.width.toFloat(), b1.height.toFloat()),
                        style = Stroke(width = if (intersectsSibling) 4f else 2f)
                    )
                }
            }
        }
    }
}

fun Rect.overlapsStrictly(other: Rect): Boolean {
    return left < other.right - 0.5f &&
        right > other.left + 0.5f &&
        top < other.bottom - 0.5f &&
        bottom > other.top + 0.5f
}
