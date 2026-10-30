package com.example.ui.hud

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.ItemType
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RulerYellow

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
        // Rounded Square Item Slot with Roulette Animation
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 96.dp, height = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            badgeColor.copy(alpha = if (activeItem != ItemType.NONE) 0.85f else 0.45f),
                            Color(0xD90F172A)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    color = if (isRouletteSpinning) RulerYellow else badgeColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onTapItemSlot)
                .testTag("hud_item_slot")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = if (isRouletteSpinning) "SPINNING..." else "ITEM",
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (isRouletteSpinning) RulerYellow else Color(0xFFCBD5E1)
                )
                Text(
                    text = activeItem.label,
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 12.sp
                )
            }
        }

        // Pause Button (min 48dp touch target)
        IconButton(
            onClick = onPauseClicked,
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xB30F172A), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                .testTag("hud_pause_button")
        ) {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = stringResource(R.string.cd_pause),
                tint = Color.White
            )
        }
    }
}
