package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout. union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileAndSettings
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.JoinBlue
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateSurface

@Composable
fun MainMenuScreen(
    profile: UserProfileAndSettings,
    onQuickRace: () -> Unit,
    onHostRace: () -> Unit,
    onJoinRace: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHowToConnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x88090D16),
                        Color(0xCC0F172A),
                        Color(0xF20F172A)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .testTag("main_menu_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Half: Big Animated Title "DESK RACER" + "LAN" Chip + Showroom Driver Badge
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "DESK RACER",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ArcadeCrimson
                        ) {
                            Text(
                                text = "LAN",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = "ZERO-INTERNET WI-FI HOTSPOT MICRO-KART RACING",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = SkyCyan,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Bottom-left Player Profile & "How to connect" link
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xCC1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(profile.kartColorHex))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(profile.kartColorHex), RoundedCornerShape(8.dp))
                            )
                            Text(
                                text = "${profile.playerName} • ${profile.kartBody.title.uppercase()}",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clickable(onClick = onOpenHowToConnect)
                            .padding(horizontal = 8.dp)
                            .testTag("btn_how_to_connect"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = RulerYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "How to connect over Wi-Fi Hotspot (Zero Internet)",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = RulerYellow
                        )
                    }
                }
            }

            // Right Half: Vertical Stack of 5 Large Action Buttons (each >= 54-56dp tall)
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.End
            ) {
                MenuActionButton(
                    title = "QUICK RACE",
                    subtitle = "Solo vs 5 CPU Racers",
                    icon = Icons.Default.Flag,
                    accentColor = ArcadeCrimson,
                    testTag = "btn_menu_quick_race",
                    onClick = onQuickRace
                )
                MenuActionButton(
                    title = "HOST RACE",
                    subtitle = "Create Room on Hotspot / Wi-Fi",
                    icon = Icons.Default.WifiTethering,
                    accentColor = HostEmerald,
                    testTag = "btn_menu_host_race",
                    onClick = onHostRace
                )
                MenuActionButton(
                    title = "JOIN RACE",
                    subtitle = "Auto-Discover or Enter Host IP",
                    icon = Icons.Default.Wifi,
                    accentColor = JoinBlue,
                    testTag = "btn_menu_join_race",
                    onClick = onJoinRace
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactMenuButton(
                        title = "GARAGE",
                        icon = Icons.Default.Build,
                        accentColor = DriftAmber,
                        testTag = "btn_menu_garage",
                        onClick = onOpenGarage,
                        modifier = Modifier.weight(1f)
                    )
                    CompactMenuButton(
                        title = "SETTINGS",
                        icon = Icons.Default.Settings,
                        accentColor = SkyCyan,
                        testTag = "btn_menu_settings",
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .border(1.5.dp, accentColor.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor)
                }
                Column {
                    Text(
                        text = title,
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            Text(
                text = "▶",
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = accentColor
            )
        }
    }
}

@Composable
private fun CompactMenuButton(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
        modifier = modifier
            .height(56.dp)
            .border(1.5.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            Text(
                text = title,
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
        }
    }
}
