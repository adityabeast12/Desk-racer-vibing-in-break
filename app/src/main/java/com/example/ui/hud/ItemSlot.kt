package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.render.AssetRepository
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.HotPink
import com.example.ui.theme.InkSurface
import com.example.ui.theme.LampAmber
import com.example.ui.theme.TextPrimary

val ItemType.shortLabel: String
    get() = when (this) {
        ItemType.NONE -> "EMPTY"
        ItemType.MARBLE -> "MARBLE"
        ItemType.PAPER_PLANE -> "PLANE"
        ItemType.BATTERY -> "TURBO"
        ItemType.ERASER -> "ERASER"
        ItemType.TRIPLE_TACKS -> "TACKS"
        ItemType.MAGNET -> "MAGNET"
    }

val ItemType.uiIconAssetPath: String
    get() = when (this) {
        ItemType.NONE -> "textures/ui/items/item_box.png"
        ItemType.MARBLE -> "textures/ui/items/marble.png"
        ItemType.PAPER_PLANE -> "textures/ui/items/paper_plane.png"
        ItemType.BATTERY -> "textures/ui/items/battery.png"
        ItemType.ERASER -> "textures/ui/items/eraser.png"
        ItemType.TRIPLE_TACKS -> "textures/ui/items/triple_tacks.png"
        ItemType.MAGNET -> "textures/ui/items/magnet.png"
    }

/**
 * Part D5: Renders the 256x256 PNG icon from `textures/ui/items/<item>.png` (loaded & cached via
 * `AssetRepository.loadUiBitmapStatic`), falling back to a procedural icon only if the asset is missing.
 */
@Composable
fun ItemModelMiniGlyph(
    item: ItemType,
    modifier: Modifier = Modifier,
    showItemBoxWhenEmpty: Boolean = false
) {
    val context = LocalContext.current
    val assetPath = remember(item, showItemBoxWhenEmpty) {
        if (item == ItemType.NONE && !showItemBoxWhenEmpty) {
            "textures/ui/items/star.png"
        } else {
            item.uiIconAssetPath
        }
    }
    val bitmap = remember(assetPath) {
        AssetRepository.loadUiBitmapStatic(context, assetPath)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            alpha = if (item == ItemType.NONE) 0.55f else 1.0f,
            modifier = modifier
        )
    } else {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.5f
            when (item) {
                ItemType.NONE -> {
                    drawCircle(color = Color(0x44FFFFFF), radius = w * 0.36f, center = Offset(cx, cy))
                    drawCircle(color = LampAmber.copy(alpha = 0.7f), radius = w * 0.18f, center = Offset(cx, cy))
                }
                ItemType.MARBLE -> {
                    drawCircle(color = Color(0xFF0284C7), radius = w * 0.42f, center = Offset(cx, cy))
                    drawCircle(color = BoostCyan, radius = w * 0.28f, center = Offset(cx - w * 0.08f, cy - h * 0.08f))
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
                }
                ItemType.ERASER -> {
                    drawRoundRect(
                        color = HotPink,
                        topLeft = Offset(w * 0.14f, h * 0.28f),
                        size = Size(w * 0.72f, h * 0.44f),
                        cornerRadius = CornerRadius(6f, 6f)
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
}
