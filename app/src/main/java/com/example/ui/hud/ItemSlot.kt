package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.ItemType
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.HotPink
import com.example.ui.theme.InkBorder
import com.example.ui.theme.InkSurface
import com.example.ui.theme.LampAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Part B4, E1, E3: TOP_RIGHT item slot + pause button.
 * Pause is always on the far right with a 56.dp hit area (even though its icon is 24.dp),
 * and the item slot sits to the left of it with >= 10.dp spacing.
 */
@Composable
fun ItemSlot(
    heldItem: ItemType,
    isRouletteSpinning: Boolean,
    rouletteDisplayItem: ItemType,
    onTapItemSlot: () -> Unit,
    onPauseClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeItem = if (isRouletteSpinning) rouletteDisplayItem else heldItem
    val badgeColor = Color(activeItem.badgeColorHex)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Item Slot (left of pause button, >= 56.dp height)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 88.dp, height = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            badgeColor.copy(alpha = if (activeItem != ItemType.NONE) 0.55f else 0.25f),
                            InkSurface.copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = if (isRouletteSpinning) LampAmber else badgeColor.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(role = Role.Button, onClick = onTapItemSlot)
                .testTag("hud_item_slot")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                ItemModelMiniGlyph(
                    item = activeItem,
                    modifier = Modifier.size(26.dp)
                )
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = if (isRouletteSpinning) "SPIN" else "ITEM",
                        style = DRType.Caption.copy(fontSize = 9.sp, fontWeight = FontWeight.W800),
                        color = if (isRouletteSpinning) LampAmber else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeItem.shortLabel,
                        style = DRType.Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.W700),
                        color = TextPrimary,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Part E3: Pause button with a 56.dp hit area on the far right (icon 24.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(InkSurface.copy(alpha = 0.90f))
                .border(1.5.dp, InkBorder, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onPauseClicked)
                .testTag("hud_pause_button")
        ) {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = stringResource(R.string.cd_pause),
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private val ItemType.shortLabel: String
    get() = when (this) {
        ItemType.NONE -> "EMPTY"
        ItemType.MARBLE -> "MARBLE"
        ItemType.PAPER_PLANE -> "PLANE"
        ItemType.BATTERY -> "TURBO"
        ItemType.ERASER -> "ERASER"
        ItemType.TRIPLE_TACKS -> "TACKS"
        ItemType.MAGNET -> "MAGNET"
    }

/**
 * 3D-shaded silhouette icon matching the DRM2 item models (battery, thumbtack, eraser,
 * marble, paper_plane, magnet, star_sticker).
 */
@Composable
fun ItemModelMiniGlyph(
    item: ItemType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        when (item) {
            ItemType.NONE -> {
                // Star sticker outline
                drawCircle(color = Color(0x44FFFFFF), radius = w * 0.36f, center = Offset(cx, cy))
                drawCircle(color = LampAmber.copy(alpha = 0.7f), radius = w * 0.18f, center = Offset(cx, cy))
            }
            ItemType.MARBLE -> {
                drawCircle(color = Color(0xFF0284C7), radius = w * 0.42f, center = Offset(cx, cy))
                drawCircle(color = BoostCyan, radius = w * 0.28f, center = Offset(cx - w * 0.08f, cy - h * 0.08f))
                drawCircle(color = Color.White, radius = w * 0.10f, center = Offset(cx - w * 0.14f, cy - h * 0.14f))
            }
            ItemType.PAPER_PLANE -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.65f)
                    lineTo(w * 0.88f, h * 0.25f)
                    lineTo(w * 0.55f, h * 0.85f)
                    close()
                }
                drawPath(p, color = Color(0xFFF8FAFC))
            }
            ItemType.BATTERY -> {
                drawRoundRect(
                    color = Color(0xFF16A34A),
                    topLeft = Offset(w * 0.24f, h * 0.22f),
                    size = Size(w * 0.52f, h * 0.64f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRect(
                    color = LampAmber,
                    topLeft = Offset(w * 0.36f, h * 0.10f),
                    size = Size(w * 0.28f, h * 0.12f)
                )
            }
            ItemType.ERASER -> {
                drawRoundRect(
                    color = HotPink,
                    topLeft = Offset(w * 0.14f, h * 0.28f),
                    size = Size(w * 0.72f, h * 0.44f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRect(
                    color = Color(0xFF3B82F6),
                    topLeft = Offset(w * 0.14f, h * 0.28f),
                    size = Size(w * 0.26f, h * 0.44f)
                )
            }
            ItemType.TRIPLE_TACKS -> {
                drawCircle(color = LampAmber, radius = w * 0.20f, center = Offset(w * 0.32f, h * 0.62f))
                drawCircle(color = LampAmber, radius = w * 0.20f, center = Offset(w * 0.68f, h * 0.62f))
                drawCircle(color = LampAmber, radius = w * 0.20f, center = Offset(w * 0.50f, h * 0.32f))
            }
            ItemType.MAGNET -> {
                drawCircle(color = Color(0xFFEF4444), radius = w * 0.38f, center = Offset(cx, cy))
                drawCircle(color = InkSurface, radius = w * 0.20f, center = Offset(cx, cy))
            }
        }
    }
}
