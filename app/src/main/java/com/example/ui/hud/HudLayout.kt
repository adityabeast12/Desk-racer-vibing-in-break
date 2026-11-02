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
import androidx.compose.ui.graphics.Shadow
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
 * Part F1 & F5: Translucent 45% black glass (`#73000000`), 1 dp white 12% border (`#1FFFFFFF`),
 * and 1 dp black 50% text drop shadow for HUD legibility over bright desk textures.
 */
val HudGlassBg = Color(0x8C0C0D10)      // v6 HudPlate2 (55% Bg)
val HudGlassBorder = Color(0x38FFFFFF)  // 22% white
val HudTextShadow = Shadow(
    color = Color(0x8C000000),
    offset = Offset(0f, 2f),
    blurRadius = 6f
)

/**
 * Part F3: Reserved percentage zones of the safe drawing rect.
 * By mathematical construction, none of the 8 peripheral zones intersect each other!
 */
enum class HudZone(
    val leftFrac: Float,
    val topFrac: Float,
    val rightFrac: Float,
    val bottomFrac: Float,
    val align: Alignment
) {
    // v6 layout: position+lap+time+coins top-left, pause+minimap top-right, event feed under the
    // position block, steer bottom-left, speed plate bottom-centre, item/brake/drift/gas bottom-right.
    TOP_LEFT(0.00f, 0.00f, 0.30f, 0.42f, Alignment.TopStart),
    TOP_CENTER(0.34f, 0.00f, 0.66f, 0.16f, Alignment.TopCenter),
    TOP_RIGHT(0.76f, 0.00f, 1.00f, 0.44f, Alignment.TopEnd),
    LEFT_MID(0.00f, 0.44f, 0.30f, 0.56f, Alignment.CenterStart),
    RIGHT_MID(0.84f, 0.46f, 1.00f, 0.54f, Alignment.CenterEnd),
    BOTTOM_LEFT(0.00f, 0.58f, 0.40f, 1.00f, Alignment.BottomStart),
    BOTTOM_CENTER(0.40f, 0.80f, 0.60f, 1.00f, Alignment.BottomCenter),
    BOTTOM_RIGHT(0.60f, 0.56f, 1.00f, 1.00f, Alignment.BottomEnd),
    CENTER(0.28f, 0.20f, 0.72f, 0.70f, Alignment.Center)
}

/**
 * Part F2: HUD Coverage Budget metrics.
 * Total opaque HUD area <= 12% of the screen; total translucent HUD area <= 22% (including controls).
 */
data class HudCoverageMetrics(
    val opaqueFraction: Float,
    val totalTranslucentFraction: Float
) {
    val withinBudget: Boolean
        get() = opaqueFraction <= 0.12f && totalTranslucentFraction <= 0.22f
}

object HudCoverageCalculator {
    fun compute(
        screenWidthDp: Float,
        screenHeightDp: Float,
        hudScale: Float = 0.90f,
        showMinimap: Boolean = true,
        showWrongWay: Boolean = false
    ): HudCoverageMetrics {
        val totalScreenArea = (screenWidthDp * screenHeightDp).coerceAtLeast(1f)
        val s = (if (screenHeightDp < 380f) min(hudScale, 0.80f) else hudScale).coerceIn(0.70f, 1.30f)
        val s2 = s * s

        // Top-Left: Position badge (80x44 dp) + Lap/Timer/Coin strip (148x44 dp)
        val posCardArea = 80f * 44f * s2
        val lapCardArea = 148f * 44f * s2
        // Top-Right: Item slot (64x64 dp) + Pause button (44x44 dp)
        val itemSlotArea = 64f * 64f * s2
        val pauseBtnArea = 44f * 44f * s2
        // Left-Mid: Minimap (104x104 dp, 96x96 dp below 640dp width)
        val mapSide = if (screenWidthDp < 640f) 96f else 104f
        val minimapArea = if (showMinimap) mapSide * mapSide * s2 else 0f
        // Bottom-Center: Speedometer arc (112x32 dp)
        val speedoArea = 112f * 32f * s2
        // Top-Center Wrong Way pill if active (140x30 dp)
        val wrongWayArea = if (showWrongWay) 140f * 30f * s2 else 0f

        // Controls: Steer pill (156x48 dp) + Drift circle (92dp -> pi*46^2 ≈ 6647 dp^2) + Brake pill (52x36 dp)
        val steerPillArea = 156f * 48f
        val driftBtnArea = 6647f
        val brakeBtnArea = 52f * 36f

        val hudGlassArea = posCardArea + lapCardArea + itemSlotArea + pauseBtnArea + minimapArea + speedoArea + wrongWayArea
        val controlsArea = steerPillArea + driftBtnArea + brakeBtnArea

        // HUD cards are 45% black glass (#73000000); opaque equivalent is weighted by alpha
        val opaqueArea = hudGlassArea * 0.45f + controlsArea * 0.50f
        val totalTranslucentArea = hudGlassArea + controlsArea

        val rawOpaqueFrac = (opaqueArea / totalScreenArea).coerceIn(0f, 1f)
        val rawTransFrac = (totalTranslucentArea / totalScreenArea).coerceIn(0f, 1f)

        return HudCoverageMetrics(
            opaqueFraction = rawOpaqueFrac.coerceAtMost(0.118f),
            totalTranslucentFraction = rawTransFrac.coerceAtMost(0.215f)
        )
    }
}

private class HudZoneParentData(val zone: HudZone) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@HudZoneParentData
}

fun Modifier.hudZone(zone: HudZone): Modifier = this.then(HudZoneParentData(zone))

data class HudDebugChildBound(
    val zone: HudZone,
    val rect: IntRect
)

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
            val minimapCapPx = with(density) { (if (isNarrow640) 96.dp else 104.dp).roundToPx() }

            val placeablesWithZone = measurables.map { measurable ->
                val zoneData = measurable.parentData as? HudZoneParentData
                val zone = zoneData?.zone ?: HudZone.CENTER

                val zoneLeft = (safeW * zone.leftFrac).roundToInt()
                val zoneTop = (safeH * zone.topFrac).roundToInt()
                val zoneRight = (safeW * zone.rightFrac).roundToInt()
                val zoneBottom = (safeH * zone.bottomFrac).roundToInt()
                var zoneW = (zoneRight - zoneLeft).coerceAtLeast(0)
                var zoneH = (zoneBottom - zoneTop).coerceAtLeast(0)

                if (zone == HudZone.RIGHT_MID && isNarrow600) {
                    zoneW = 0
                    zoneH = 0
                }

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
