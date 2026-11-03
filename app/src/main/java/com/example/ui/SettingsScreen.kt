package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileAndSettings
import com.example.game.ControlSchemeType
import com.example.game.CpuDifficulty
import com.example.game.GraphicsQuality
import com.example.game.HeadlessRaceTest
import com.example.game.TrackQaResult
import com.example.render.AssetRepository
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRCloseButton
import com.example.ui.components.DRLabel
import com.example.ui.components.DRPanel
import com.example.ui.components.DRScreenHeader
import com.example.ui.components.DRSegmented
import com.example.ui.components.DRSlider
import com.example.ui.components.DRToggle
import com.example.ui.hud.HudCoverageMetrics
import com.example.ui.theme.DR
import com.example.ui.theme.DRType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private data class SettingsCat(val title: String, val icon: ImageVector)

private val CATS = listOf(
    SettingsCat("Controls", Icons.Rounded.SportsEsports),
    SettingsCat("HUD", Icons.Rounded.Dashboard),
    SettingsCat("Graphics", Icons.Rounded.Tune),
    SettingsCat("Audio & feel", Icons.Rounded.VolumeUp),
    SettingsCat("About", Icons.Rounded.Info)
)

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
    onFpsModeChanged: (Int) -> Unit = {},
    latestHudCoverage: HudCoverageMetrics? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cat by remember { mutableIntStateOf(0) }
    var qaRunning by remember { mutableStateOf(false) }
    var qaResults by remember { mutableStateOf<List<TrackQaResult>?>(null) }
    var showCredits by remember { mutableStateOf(false) }
    val creditsText = remember { AssetRepository.readCreditsText(context) }

    BackHandler {
        when {
            showCredits -> showCredits = false
            qaResults != null -> qaResults = null
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DR.Bg.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("settings_screen")
    ) {
        Column(Modifier.fillMaxSize()) {
            DRScreenHeader(kicker = "Settings", title = CATS[cat].title, onBack = onBack, backTestTag = "btn_settings_back")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                DRPanel(Modifier.width(210.dp).fillMaxHeight(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                    CATS.forEachIndexed { i, c ->
                        val sel = i == cat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(if (sel) DR.AmberTint else Color.Transparent)
                                .drawBehind { if (sel) drawRect(DR.Amber, size = Size(3.dp.toPx(), size.height)) }
                                .clickable(role = Role.Tab) { cat = i }
                                .padding(horizontal = 14.dp)
                                .testTag("settings_cat_$i"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(String.format(Locale.US, "%02d", i + 1), style = DRType.NumS.copy(fontSize = 10.sp), color = if (sel) DR.Amber else DR.TextDis)
                            Icon(c.icon, contentDescription = null, tint = if (sel) DR.TextHi else DR.TextLo, modifier = Modifier.size(20.dp))
                            Text(c.title, style = DRType.BodyStrong, color = if (sel) DR.TextHi else DR.TextMid, maxLines = 1)
                        }
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (cat) {
                        0 -> {
                            SettingRow("Steering") {
                                val opts = ControlSchemeType.entries
                                DRSegmented(
                                    options = listOf("Smart", "Pro", "Tilt", "Buttons"),
                                    selected = opts.indexOf(settings.controlScheme).coerceAtLeast(0),
                                    onSelect = { onControlSchemeChanged(opts[it]) },
                                    modifier = Modifier.width(280.dp),
                                    testTagPrefix = "settings_scheme"
                                )
                            }
                            Text(settings.controlScheme.description, style = DRType.Caption, color = DR.TextLo, modifier = Modifier.padding(bottom = 4.dp))
                            DRSlider(
                                label = "Steering sensitivity",
                                value = settings.steerSensitivity,
                                onValueChange = onSensitivityChanged,
                                valueRange = 0.5f..1.8f,
                                valueText = "${(settings.steerSensitivity * 100).roundToInt()}%",
                                testTag = "settings_sensitivity"
                            )
                            DRToggle("Auto-accelerate", settings.autoAccelerate, onAutoAccelerateChanged, subtitle = "Hold brake to slow down", testTag = "settings_auto_gas")
                            DRToggle("Smart steering", settings.smartSteering, onSmartSteeringChanged, subtitle = "Gently keeps you off the track edge", testTag = "settings_smart_steer")
                            DRToggle("Left-handed layout", settings.swapHands, onSwapHandsChanged, subtitle = "Steer on the right, pedals on the left", testTag = "settings_swap_hands")
                            SettingRow("Bot skill (solo)") {
                                val opts = CpuDifficulty.entries
                                DRSegmented(listOf("Rookie", "Pro", "Champ", "Mixed"), opts.indexOf(settings.cpuDifficulty), { onDifficultyChanged(opts[it]) }, Modifier.width(280.dp), testTagPrefix = "settings_skill")
                            }
                        }
                        1 -> {
                            DRSlider(
                                label = "Button size",
                                value = settings.buttonScale,
                                onValueChange = onButtonScaleChanged,
                                valueRange = 0.85f..1.15f,
                                valueText = "${(settings.buttonScale * 100).roundToInt()}%",
                                testTag = "settings_button_size"
                            )
                            DRSlider(
                                label = "Control opacity",
                                value = settings.hudOpacity,
                                onValueChange = onHudOpacityChanged,
                                valueRange = 0.3f..1.0f,
                                valueText = "${(settings.hudOpacity * 100).roundToInt()}%",
                                testTag = "settings_opacity"
                            )
                            DRToggle("Show minimap", settings.showMinimap, onShowMinimapChanged, testTag = "settings_minimap")
                            DRToggle("Show HUD layout zones", settings.showDebugHudZones, onShowDebugHudZonesChanged, subtitle = "Developer overlay", testTag = "settings_debug_zones")
                            latestHudCoverage?.let {
                                Text(
                                    String.format(Locale.US, "HUD coverage: %.0f%% opaque · %.0f%% total", it.opaqueFraction * 100f, it.totalTranslucentFraction * 100f),
                                    style = DRType.NumS,
                                    color = DR.TextLo,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                        2 -> {
                            SettingRow("Quality") {
                                val opts = GraphicsQuality.entries
                                DRSegmented(listOf("Low", "Medium", "High"), opts.indexOf(settings.graphicsQuality), { onGraphicsChanged(opts[it]) }, Modifier.width(240.dp), testTagPrefix = "settings_quality")
                            }
                            Text(settings.graphicsQuality.label, style = DRType.Caption, color = DR.TextLo, modifier = Modifier.padding(bottom = 4.dp))
                            SettingRow("Frame rate") {
                                DRSegmented(listOf("Max", "60", "30"), settings.fpsMode.coerceIn(0, 2), { onFpsModeChanged(it) }, Modifier.width(240.dp), testTagPrefix = "settings_fps")
                            }
                            Text(
                                when (settings.fpsMode) {
                                    1 -> "Locked to 60 fps."
                                    2 -> "30 fps battery saver."
                                    else -> "Uses your screen's highest refresh rate (90 / 120 / 144 Hz). Drops to 60 while the phone is hot."
                                },
                                style = DRType.Caption,
                                color = DR.TextLo,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            DRToggle("Post FX", settings.postFxEnabled, onPostFxChanged, subtitle = "Bloom + anti-aliasing", testTag = "settings_postfx")
                            DRToggle("Contact shadows", settings.contactShadowsEnabled, onContactShadowsChanged, subtitle = "Soft shadows under karts and props", testTag = "settings_shadows")
                            Text(
                                "If the phone gets hot, the game lowers quality automatically. Game speed is never affected.",
                                style = DRType.Caption,
                                color = DR.TextLo,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                        3 -> {
                            DRToggle("Music & sound effects", settings.soundEnabled, onSoundChanged, testTag = "settings_sound")
                            DRToggle("Haptics", settings.hapticsEnabled, onHapticsChanged, subtitle = "Bumps, hits and boosts", testTag = "settings_haptics")
                        }
                        else -> {
                            Text("DESK RACER LAN", style = DRType.Title, color = DR.TextHi)
                            Text("Offline hotspot kart racing. Fonts: Poppins, Kanit, Barlow Semi Condensed (SIL OFL).", style = DRType.Caption, color = DR.TextMid, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DRButton("Credits & licenses", onClick = { showCredits = true }, variant = DRButtonVariant.SECONDARY, icon = Icons.Rounded.Info, testTag = "btn_credits")
                            }
                            Spacer(Modifier.height(18.dp))
                            DRLabel("Developer")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DRButton(
                                    text = if (qaRunning) "Running QA…" else "Run track QA",
                                    icon = Icons.Rounded.Science,
                                    variant = DRButtonVariant.SECONDARY,
                                    enabled = !qaRunning,
                                    onClick = {
                                        qaRunning = true
                                        scope.launch {
                                            val res = withContext(Dispatchers.Default) { HeadlessRaceTest.runAllTracksQa() }
                                            qaResults = res
                                            qaRunning = false
                                        }
                                    },
                                    testTag = "btn_run_track_qa"
                                )
                                DRButton("Test track loop", onClick = onLaunchTestTrackLoop, variant = DRButtonVariant.GHOST, testTag = "btn_test_track_loop")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        if (showCredits) {
            SheetOverlay(title = "Credits & licenses", onClose = { showCredits = false }, tag = "credits_modal") {
                Text(creditsText, style = DRType.Caption.copy(fontSize = 11.sp), color = DR.TextMid, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        }
        qaResults?.let { report ->
            SheetOverlay(title = "Track QA report", onClose = { qaResults = null }, tag = "qa_modal") {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(report) { r ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .drawBehind { drawRect(if (r.passed) DR.Ok else DR.Bad, size = Size(3.dp.toPx(), size.height)) }
                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text("${r.trackName} · ${if (r.passed) "PASS" else "FAIL"}", style = DRType.BodyStrong, color = if (r.passed) DR.Ok else DR.Bad)
                            Text(
                                String.format(
                                    Locale.US,
                                    "Finished %d/%d · avg lap %.1fs · slowest %.1fs · watchdogs %d",
                                    r.finishedCount, r.totalBots, r.averageLapMs / 1000f, r.slowestBotTimeMs / 1000f, r.totalWatchdogCount
                                ),
                                style = DRType.NumS,
                                color = DR.TextMid
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = DRType.BodyStrong.copy(fontSize = 13.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        control()
    }
}

@Composable
private fun SheetOverlay(title: String, onClose: () -> Unit, tag: String, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(DR.Scrim)
            .testTag(tag)
    ) {
        DRPanel(Modifier.fillMaxSize().padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = DRType.Title, color = DR.TextHi, modifier = Modifier.weight(1f))
                DRCloseButton(onClick = onClose, testTag = "btn_close_${tag}")
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}
