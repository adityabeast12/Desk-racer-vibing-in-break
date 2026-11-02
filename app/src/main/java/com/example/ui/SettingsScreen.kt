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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileAndSettings
import com.example.game.ControlSchemeType
import com.example.game.CpuDifficulty
import com.example.game.GraphicsQuality
import com.example.game.HeadlessRaceTest
import com.example.game.TrackQaResult
import com.example.render.AssetRepository
import com.example.ui.hud.HudCoverageMetrics
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.JoinBlue
import com.example.ui.theme.Poppins
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
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
    onSwapHandsChanged: (Boolean) -> Unit = {},
    onButtonScaleChanged: (Float) -> Unit = {},
    onHudScaleChanged: (Float) -> Unit = {},
    onHudOpacityChanged: (Float) -> Unit = {},
    onPostFxChanged: (Boolean) -> Unit = {},
    onContactShadowsChanged: (Boolean) -> Unit = {},
    onShowDebugHudZonesChanged: (Boolean) -> Unit = {},
    onLaunchTestTrackLoop: () -> Unit = {},
    latestHudCoverage: HudCoverageMetrics? = null,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var qaRunning by remember { mutableStateOf(false) }
    var qaResults by remember { mutableStateOf<List<TrackQaResult>?>(null) }
    var showCreditsModal by remember { mutableStateOf(false) }
    val creditsText = remember { AssetRepository.readCreditsText(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB0B1020))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("settings_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Card: Controls, Customizer, Driving Assists + Developer QA
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
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(44.dp)
                                .background(SlateSurface, RoundedCornerShape(10.dp))
                                .testTag("btn_settings_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(
                            text = "CONTROLS & HUD CUSTOMIZER",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SkyCyan
                        )
                    }

                    // 4 Control Schemes: SMART, PRO, TILT, BUTTONS
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "CONTROL SCHEME",
                            fontFamily = Poppins,
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
                                        .height(38.dp)
                                        .clickable { onControlSchemeChanged(scheme) }
                                        .testTag("settings_scheme_${scheme.id}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) SkyCyan else SlateSurface
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                                        Text(
                                            text = scheme.title.uppercase(),
                                            fontFamily = Poppins,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = if (active) SlateAbyss else Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sensitivity + Button Size + HUD Opacity Sliders
                    Column {
                        Text(
                            text = "STEER SENSITIVITY: ${(settings.steerSensitivity * 100).roundToInt()}%",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                        Slider(
                            value = settings.steerSensitivity,
                            onValueChange = onSensitivityChanged,
                            valueRange = 0.5f..1.8f,
                            colors = SliderDefaults.colors(thumbColor = SkyCyan, activeTrackColor = SkyCyan),
                            modifier = Modifier.height(26.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BUTTON SIZE: ${(settings.buttonScale * 100).roundToInt()}%",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color.White
                            )
                            Slider(
                                value = settings.buttonScale,
                                onValueChange = onButtonScaleChanged,
                                valueRange = 0.85f..1.25f,
                                colors = SliderDefaults.colors(thumbColor = RulerYellow, activeTrackColor = RulerYellow),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HUD OPACITY: ${(settings.hudOpacity * 100).roundToInt()}%",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color.White
                            )
                            Slider(
                                value = settings.hudOpacity,
                                onValueChange = onHudOpacityChanged,
                                valueRange = 0.50f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = HostEmerald, activeTrackColor = HostEmerald),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }

                    SettingToggleRow("Auto-Accelerate (Hands-Free Gas)", settings.autoAccelerate, onAutoAccelerateChanged)
                    SettingToggleRow("Smart Steering (Track Edge Assist)", settings.smartSteering, onSmartSteeringChanged)
                    SettingToggleRow("Left-Handed Swap (Steer Right / Actions Left)", settings.swapHands, onSwapHandsChanged)

                    // Developer -> Run Track QA
                    Button(
                        onClick = {
                            if (!qaRunning) {
                                qaRunning = true
                                scope.launch {
                                    val res = withContext(Dispatchers.Default) {
                                        HeadlessRaceTest.runAllTracksQa()
                                    }
                                    qaResults = res
                                    qaRunning = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JoinBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("btn_run_track_qa")
                    ) {
                        Text(
                            text = if (qaRunning) "RUNNING 8-TRACK BOT QA..." else "DEV: RUN TRACK QA (8 BOTS × 3 LAPS)",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Right Card: Graphics Quality, Post FX, Audio/Haptics, Developer HUD Zones & Credits
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
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GRAPHICS, AUDIO & CREDITS",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SkyCyan
                        )
                        Button(
                            onClick = { showCreditsModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_open_credits")
                        ) {
                            Text(
                                text = "CREDITS & LICENSES",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = RulerYellow
                            )
                        }
                    }

                    // Graphics Quality
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "GRAPHICS QUALITY",
                            fontFamily = Poppins,
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
                                        .height(36.dp)
                                        .clickable { onGraphicsChanged(q) }
                                        .testTag("settings_gfx_${q.id}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) HostEmerald else SlateSurface
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = q.name,
                                            fontFamily = Poppins,
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "CPU DIFFICULTY",
                            fontFamily = Poppins,
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
                                        .height(36.dp)
                                        .clickable { onDifficultyChanged(d) }
                                        .testTag("settings_cpu_${d.id}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) DriftAmber else SlateSurface
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = d.label.uppercase(),
                                            fontFamily = Poppins,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (active) SlateAbyss else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingToggleRow("Music & Sound Effects", settings.soundEnabled, onSoundChanged)
                    SettingToggleRow("Haptic Vibration Feedback", settings.hapticsEnabled, onHapticsChanged)
                    SettingToggleRow("Show Track Minimap", settings.showMinimap, onShowMinimapChanged)
                    SettingToggleRow("PBR Contact Shadows & Post FX", settings.contactShadowsEnabled, onContactShadowsChanged)
                    SettingToggleRow("Developer: Show HUD Layout Zones", settings.showDebugHudZones, onShowDebugHudZonesChanged)
                }
            }
        }

        // About / Credits Modal (Part B7 & G6: reads assets/CREDITS.txt verbatim)
        if (showCreditsModal) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEB0B1020))
                    .testTag("credits_modal")
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SlateAbyss,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, RulerYellow),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ABOUT DESK RACER LAN v3 • OPEN ASSET CREDITS",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = RulerYellow
                            )
                            Button(
                                onClick = { showCreditsModal = false },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("btn_close_credits")
                            ) {
                                Text("CLOSE", fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(SlateSurface, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = creditsText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Track QA Report Modal
        val report = qaResults
        if (report != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6090D16))
                    .testTag("track_qa_modal")
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SlateAbyss,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, SkyCyan),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HEADLESS TRACK QA REPORT (8 BOTS × 3 LAPS @ 60 HZ)",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = RulerYellow
                            )
                            Button(
                                onClick = { qaResults = null },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("CLOSE", fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(report) { r ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SlateSurface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (r.passed) HostEmerald else ArcadeCrimson
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${r.trackName} (${if (r.passed) "PASS" else "FAIL"})",
                                            fontFamily = Poppins,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (r.passed) HostEmerald else ArcadeCrimson
                                        )
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "Finished: %d/%d  •  Avg Lap: %.1fs  •  Slowest: %.1fs  •  Watchdogs: %d",
                                                r.finishedCount,
                                                r.totalBots,
                                                r.averageLapMs / 1000f,
                                                r.slowestBotTimeMs / 1000f,
                                                r.totalWatchdogCount
                                            ),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = Color.White
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
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = Poppins,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
