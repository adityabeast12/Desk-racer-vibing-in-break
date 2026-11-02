package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileAndSettings
import com.example.game.GameConstants
import com.example.game.KartBodyType
import com.example.ui.theme.DRColors
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.Poppins
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface

@Composable
fun GarageScreen(
    profile: UserProfileAndSettings,
    onNameChanged: (String) -> Unit,
    onColorSelected: (Int) -> Unit,
    onBodySelected: (KartBodyType) -> Unit,
    onLiverySelected: (Int) -> Unit = {},
    onPaintFinishSelected: (Int) -> Unit = {},
    onRotateTurntable: (Float, Float) -> Unit = { _, _ -> },
    onPinchZoomTurntable: (Float) -> Unit = {},
    onResetTurntable: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("garage_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left Half: Live 3D Rotating Kart Turntable + 4 Stat Bars (Speed, Accel, Handling, Weight)
            Column(
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xCC0F172A), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                            .testTag("btn_garage_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "KART GARAGE",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "6 HIGH-DETAIL 3D CHASSIS • 3 LIVERIES EACH",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = SkyCyan
                        )
                    }
                }

                // Bottom-left Stat Bars Card (Speed, Accel, Handling, Weight)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xE6141C33),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x38FFFFFF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${profile.kartBody.title.uppercase()} • ${profile.kartBody.subtitle}",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = RulerYellow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "LIVERY #${profile.kartLiveryIndex + 1}",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = SkyCyan
                            )
                        }

                        GarageStatBar("SPEED", (profile.kartBody.topSpeedFactor - 0.8f) / 0.3f, DRColors.KartGold)
                        GarageStatBar("ACCEL", (profile.kartBody.accelFactor - 0.8f) / 0.3f, DRColors.MintFinish)
                        GarageStatBar("HANDLING", (profile.kartBody.handlingFactor - 0.8f) / 0.3f, DRColors.ElectricBlue)
                        GarageStatBar("WEIGHT", (profile.kartBody.mass - 0.7f) / 0.65f, DRColors.DriftOrange)
                    }
                }
            }

            // Right Half: Name Input (max 10 chars), 8 Colour Swatches, 3 Livery Chips, 6 Kart Bodies (2x3 grid)
            Surface(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEB141C33),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Player Name Input + Livery Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "DRIVER NAME (MAX 10)",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = SkyCyan
                            )
                            BasicTextField(
                                value = profile.playerName,
                                onValueChange = { onNameChanged(it.take(10).uppercase()) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                ),
                                cursorBrush = SolidColor(SkyCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(SlateSurface, RoundedCornerShape(10.dp))
                                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("garage_name_input")
                            )
                        }

                        // 3 Livery Variant Buttons
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "LIVERY TRIM",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = RulerYellow
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("STOCK", "CYBER", "PRO").forEachIndexed { idx, label ->
                                    val selected = profile.kartLiveryIndex == idx
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) RulerYellow else SlateSurface,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clickable { onLiverySelected(idx) }
                                            .testTag("garage_livery_$idx")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontFamily = Poppins,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = if (selected) SlateAbyss else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. 8 Kart Paint Swatches
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "KART PAINT COLOUR",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = SkyCyan
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GameConstants.KART_COLORS.forEachIndexed { idx, hex ->
                                val selected = profile.kartColorIndex == idx
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) Color.White else Color(0x55000000),
                                            shape = CircleShape
                                        )
                                        .clickable { onColorSelected(idx) }
                                        .testTag("garage_color_$idx")
                                )
                            }
                        }
                    }

                    // 3. 6 Kart Bodies (2 rows of 3: Roadster, Pencil, Monster + Origami, Tank, Classic Toy)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "3D CHASSIS BODY (6 MODELS)",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = SkyCyan
                        )
                        val bodies = KartBodyType.entries
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            bodies.take(3).forEach { body ->
                                val selected = profile.kartBody == body
                                Button(
                                    onClick = { onBodySelected(body) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) HostEmerald else SlateSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("garage_body_${body.id}")
                                ) {
                                    Text(
                                        text = body.title.uppercase(),
                                        fontFamily = Poppins,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = if (selected) SlateAbyss else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            bodies.drop(3).forEach { body ->
                                val selected = profile.kartBody == body
                                Button(
                                    onClick = { onBodySelected(body) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) HostEmerald else SlateSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("garage_body_${body.id}")
                                ) {
                                    Text(
                                        text = body.title.uppercase(),
                                        fontFamily = Poppins,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = if (selected) SlateAbyss else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GarageStatBar(
    label: String,
    fraction: Float,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = DRColors.MutedSlate,
            modifier = Modifier.weight(0.28f)
        )
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0.18f, 1f) },
            color = color,
            trackColor = Color(0xFF1E293B),
            modifier = Modifier
                .weight(0.72f)
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun KartThumbnailImage(
    body: KartBodyType,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = remember(body) {
        val slug = body.drmAssetPath
            .substringAfterLast("kart_")
            .substringBefore(".drm")
        com.example.render.AssetRepository.loadUiBitmapStatic(
            context,
            "textures/ui/karts/$slug.png"
        )?.asImageBitmap()
    }
    Box(
        modifier = modifier
            .background(Color(0x660F172A), RoundedCornerShape(8.dp))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = body.title,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(14.dp)
                    .background(accentColor, RoundedCornerShape(4.dp))
            )
        }
    }
}

