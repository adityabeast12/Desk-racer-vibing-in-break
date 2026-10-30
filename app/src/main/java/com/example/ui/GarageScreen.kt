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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileAndSettings
import com.example.game.GameConstants
import com.example.game.KartBodyType
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.OrbitronFontFamily
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
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("garage_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Half: Live 3D Rotating Kart Preview Window + Stat Bars
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
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                        Text(
                            text = "5 HIGH-DETAIL 3D CHASSIS • LIVE TURNTABLE",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = SkyCyan
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xD90F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x38FFFFFF))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${profile.kartBody.title} • ${profile.kartBody.subtitle}",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = RulerYellow
                        )
                        Text(
                            text = "SPEED ${(profile.kartBody.topSpeedFactor * 100).toInt()}%  •  ACCEL ${(profile.kartBody.accelFactor * 100).toInt()}%  •  HANDLING ${(profile.kartBody.handlingFactor * 100).toInt()}%  •  MASS ${(profile.kartBody.mass * 100).toInt()}%",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = SkyCyan
                        )
                    }
                }
            }

            // Right Half: Name Input (max 10 chars), 8 Colour Swatches, 5 Kart Bodies
            Surface(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xE60F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Player Name Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "DRIVER NAME (MAX 10 CHARS)",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = SkyCyan
                        )
                        BasicTextField(
                            value = profile.playerName,
                            onValueChange = { onNameChanged(it.take(10).uppercase()) },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            ),
                            cursorBrush = SolidColor(SkyCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(SlateSurface, RoundedCornerShape(10.dp))
                                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("garage_name_input")
                        )
                    }

                    // 2. 8 Kart Paint Swatches
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "KART PAINT COLOUR",
                            fontFamily = OrbitronFontFamily,
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
                                        .size(36.dp)
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

                    // 3. 5 Kart Bodies (2 rows: 3 + 2)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "3D CHASSIS BODY (5 MODELS)",
                            fontFamily = OrbitronFontFamily,
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
                                        .height(44.dp)
                                        .testTag("garage_body_${body.id}")
                                ) {
                                    Text(
                                        text = body.title.uppercase(),
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = if (selected) SlateAbyss else Color.White,
                                        maxLines = 1
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
                                        .height(44.dp)
                                        .testTag("garage_body_${body.id}")
                                ) {
                                    Text(
                                        text = body.title.uppercase(),
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = if (selected) SlateAbyss else Color.White,
                                        maxLines = 1
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
