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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileAndSettings
import com.example.game.ControlSchemeType
import com.example.game.CpuDifficulty
import com.example.game.GraphicsQuality
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: UserProfileAndSettings,
    onControlSchemeChanged: (ControlSchemeType) -> Unit,
    onSensitivityChanged: (Float) -> Unit,
    onAutoAccelerateChanged: (Boolean) -> Unit,
    onSmartSteeringChanged: (Boolean) -> Unit,
    onShowMinimapChanged: (Boolean) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onGraphicsChanged: (GraphicsQuality) -> Unit,
    onDifficultyChanged: (CpuDifficulty) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6090D16))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("settings_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Card: Controls & Driving Assists
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = SlateAbyss,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .background(SlateSurface, RoundedCornerShape(10.dp))
                                .testTag("btn_settings_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(
                            text = "CONTROLS & ASSISTS",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SkyCyan
                        )
                    }

                    // Control Scheme Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "STEERING SCHEME",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = RulerYellow
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ControlSchemeType.entries.forEach { scheme ->
                                val active = settings.controlScheme == scheme
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clickable { onControlSchemeChanged(scheme) }
                                        .testTag("settings_scheme_${scheme.id}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) SkyCyan else SlateSurface
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                        Text(
                                            text = scheme.title.uppercase(),
                                            fontFamily = OrbitronFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = if (active) SlateAbyss else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sensitivity Slider
                    Column {
                        Text(
                            text = "STEER SENSITIVITY: ${(settings.steerSensitivity * 100).roundToInt()}%",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                        Slider(
                            value = settings.steerSensitivity,
                            onValueChange = onSensitivityChanged,
                            valueRange = 0.5f..1.8f,
                            colors = SliderDefaults.colors(thumbColor = SkyCyan, activeTrackColor = SkyCyan),
                            modifier = Modifier.height(32.dp)
                        )
                    }

                    // Auto-Accelerate & Smart Steering Toggles
                    SettingToggleRow(
                        label = "Auto-Accelerate (Hands-Free Gas)",
                        checked = settings.autoAccelerate,
                        onCheckedChange = onAutoAccelerateChanged
                    )
                    SettingToggleRow(
                        label = "Smart Steering (Track Assist)",
                        checked = settings.smartSteering,
                        onCheckedChange = onSmartSteeringChanged
                    )
                }
            }

            // Right Card: Graphics Quality, Audio/Haptics, Minimap, CPU Difficulty
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = SlateAbyss,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "GRAPHICS, AUDIO & AI",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SkyCyan
                    )

                    // Graphics Quality
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "GRAPHICS QUALITY",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = RulerYellow
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GraphicsQuality.entries.forEach { q ->
                                val active = settings.graphicsQuality == q
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clickable { onGraphicsChanged(q) }
                                        .testTag("settings_gfx_${q.id}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) HostEmerald else SlateSurface
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                        Text(
                                            text = q.name,
                                            fontFamily = OrbitronFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (active) SlateAbyss else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CPU Difficulty
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CPU DIFFICULTY",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = RulerYellow
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CpuDifficulty.entries.forEach { d ->
                                val active = settings.cpuDifficulty == d
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clickable { onDifficultyChanged(d) }
                                        .testTag("settings_cpu_${d.id}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) DriftAmber else SlateSurface
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = d.label.uppercase(),
                                            fontFamily = OrbitronFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (active) SlateAbyss else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingToggleRow("Synthesized Sound Effects", settings.soundEnabled, onSoundChanged)
                    SettingToggleRow("Haptic Vibration Feedback", settings.hapticsEnabled, onHapticsChanged)
                    SettingToggleRow("Show Track Minimap", settings.showMinimap, onShowMinimapChanged)
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
