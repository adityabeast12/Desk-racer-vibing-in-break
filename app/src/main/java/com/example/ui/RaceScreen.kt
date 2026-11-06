package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileAndSettings
import com.example.game.CupType
import com.example.game.GameMode
import com.example.game.HudState
import com.example.game.InputState
import com.example.game.RacePhase
import com.example.game.RacerStanding
import com.example.game.Tracks
import com.example.ui.components.DRBadge
import com.example.ui.components.DRBadgeKind
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonSize
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRDialog
import com.example.ui.components.DRKerbStrip
import com.example.ui.components.DRLabel
import com.example.ui.components.DRPanel
import com.example.ui.components.KartThumb
import com.example.ui.controls.ActionButtons
import com.example.ui.controls.SteerZone
import com.example.ui.hud.CountdownOverlay
import com.example.ui.hud.HudCoverageMetrics
import com.example.ui.hud.HudEventFeed
import com.example.ui.hud.HudLayout
import com.example.ui.hud.HudPauseAndMap
import com.example.ui.hud.HudZone
import com.example.ui.hud.PositionBadge
import com.example.ui.hud.Speedometer
import com.example.ui.hud.hudZone
import com.example.ui.theme.DR
import com.example.ui.theme.DRType
import com.example.ui.theme.HudFontScaleScope

@Composable
fun RaceScreen(
    hudState: HudState,
    profile: UserProfileAndSettings,
    inputState: InputState,
    isMultiplayer: Boolean,
    isHost: Boolean,
    applySafeInsets: Boolean = true,
    onSkipFlyby: () -> Unit = {},
    onPauseChanged: (Boolean) -> Unit,
    onRestartRace: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuitToMenu: () -> Unit,
    onHudCoverageMeasured: (HudCoverageMetrics) -> Unit = {},
    externalPauseSignal: Int = 0,
    inputSource: com.example.game.InputSource = com.example.game.InputSource.TOUCH,
    onRaceCaptureChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var pauseMenuOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var photoModeActive by remember { mutableStateOf(false) }
    var photoSavedFeedback by remember { mutableStateOf<String?>(null) }
    var photoOrbitDeg by remember { mutableFloatStateOf(0f) }

    fun setPaused(p: Boolean) {
        pauseMenuOpen = p
        onPauseChanged(p)
    }

    // v7.3: the app went to the background mid-race (notification shade, call, screen off). The sim
    // was paused by the Activity but no menu opened, so the race just looked frozen. Show the menu.
    // v7.5: hardware controls drive the kart only while the race runs (menus keep focus navigation)
    val capture = !pauseMenuOpen && (hudState.phase == com.example.game.RacePhase.RACING || hudState.phase == com.example.game.RacePhase.COUNTDOWN)
    androidx.compose.runtime.LaunchedEffect(capture) { onRaceCaptureChanged(capture) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onRaceCaptureChanged(false) } }
    val touchControls = inputSource == com.example.game.InputSource.TOUCH

    val pauseSignalAtEntry = remember { externalPauseSignal }
    androidx.compose.runtime.LaunchedEffect(externalPauseSignal) {
        if (externalPauseSignal != pauseSignalAtEntry && !isMultiplayer && !pauseMenuOpen) setPaused(true)
    }

    BackHandler {
        when {
            confirmLeave -> confirmLeave = false
            photoModeActive -> photoModeActive = false
            else -> setPaused(!pauseMenuOpen)
        }
    }

    HudFontScaleScope {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("race_screen")
        ) {
            // 0. Pre-race fly-by: track title card bottom-left, Skip bottom-right
            if (hudState.phase == RacePhase.FLYBY_INTRO) {
                val track = Tracks.byId(hudState.trackId)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onSkipFlyby)
                        .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to DR.Bg.copy(alpha = 0.85f)))
                        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .testTag("flyby_overlay")
                ) {
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DRBadge(track.difficultyLabel, DRBadgeKind.AMBER)
                            DRLabel("${CupType.fromId(track.cupId).title} · ${hudState.gameMode.title}")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(track.name.uppercase(), style = DRType.Display.copy(fontSize = 48.sp, lineHeight = 46.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.subtitle, style = DRType.Body, color = DR.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DRButton(
                        text = "Skip",
                        icon = Icons.Rounded.SkipNext,
                        variant = DRButtonVariant.SECONDARY,
                        onClick = onSkipFlyby,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        testTag = "btn_skip_flyby"
                    )
                }
                return@HudFontScaleScope
            }

            // Photo mode: HUD hidden
            if (photoModeActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
                        .padding(20.dp)
                        .testTag("photo_mode_overlay")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DRButton(
                            text = "Exit",
                            variant = DRButtonVariant.SECONDARY,
                            onClick = { photoModeActive = false },
                            testTag = "btn_exit_photo_mode"
                        )
                        photoSavedFeedback?.let {
                            Text(it, style = DRType.BodyStrong, color = DR.Ok)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            DRButton(
                                text = "Orbit",
                                variant = DRButtonVariant.SECONDARY,
                                onClick = { photoOrbitDeg = (photoOrbitDeg + 45f) % 360f },
                                testTag = "btn_orbit_photo_cam"
                            )
                            DRButton(
                                text = "Save photo",
                                icon = Icons.Rounded.CameraAlt,
                                onClick = {
                                    val savedPath = saveDeskRacerPhotoToMediaStore(
                                        context = context,
                                        trackName = Tracks.byId(hudState.trackId).name,
                                        playerName = profile.playerName
                                    )
                                    photoSavedFeedback = if (savedPath != null) "Saved to Pictures/DeskRacer" else "Photo captured"
                                },
                                testTag = "btn_save_photo"
                            )
                        }
                    }
                }
                return@HudFontScaleScope
            }

            val steerZoneEnum = if (profile.leftHandedMirror) HudZone.BOTTOM_RIGHT else HudZone.BOTTOM_LEFT
            val actionsZoneEnum = if (profile.leftHandedMirror) HudZone.BOTTOM_LEFT else HudZone.BOTTOM_RIGHT
            val sorted = hudState.standings.sortedBy { it.position }

            if (!pauseMenuOpen) HudLayout(
                applySafeInsets = applySafeInsets,
                showDebugZones = profile.showHudDebugZones,
                modifier = Modifier.fillMaxSize()
            ) {
                PositionBadge(
                    position = hudState.position,
                    totalRacers = hudState.totalRacers,
                    lap = hudState.lap,
                    totalLaps = hudState.totalLaps,
                    isFinished = hudState.isFinished,
                    coinsHeld = hudState.coinsHeld,
                    positionDeltaChip = hudState.positionDeltaChip,
                    balloonsRemaining = if (hudState.gameMode == GameMode.BATTLE_BALLOONS) hudState.balloonsRemaining else -1,
                    raceTimeMs = hudState.raceTimeMs,
                    modifier = Modifier.hudZone(HudZone.TOP_LEFT)
                )

                HudPauseAndMap(
                    trackId = hudState.trackId,
                    dots = hudState.minimapDots,
                    showMinimap = profile.showMinimap,
                    onPause = { setPaused(true) },
                    leaderSlot = sorted.getOrNull(0)?.slotIndex ?: -1,
                    secondSlot = sorted.getOrNull(1)?.slotIndex ?: -1,
                    modifier = Modifier.hudZone(HudZone.TOP_RIGHT)
                )

                HudEventFeed(
                    message = hudState.toastText,
                    modifier = Modifier.hudZone(HudZone.LEFT_MID)
                )

                if (touchControls) SteerZone(
                    inputState = inputState,
                    controlScheme = profile.controlScheme,
                    sensitivity = profile.steerSensitivity,
                    opacity = profile.controlsOpacity,
                    modifier = Modifier.hudZone(steerZoneEnum)
                )

                Speedometer(
                    speedKmh = hudState.speedKmh,
                    speedRatio = hudState.speedRatio,
                    isDrifting = hudState.isDrifting,
                    driftChargeRatio = hudState.driftChargeRatio,
                    sparkTier = hudState.sparkTier,
                    modifier = Modifier.hudZone(HudZone.BOTTOM_CENTER)
                )

                if (!touchControls) com.example.ui.controls.HardwareControlsHud(
                    source = inputSource,
                    heldItem = hudState.heldItem,
                    isRouletteSpinning = hudState.isRouletteSpinning,
                    rouletteDisplayItem = hudState.rouletteDisplayItem,
                    modifier = Modifier.hudZone(actionsZoneEnum)
                ) else ActionButtons(
                    inputState = inputState,
                    autoAccelerate = profile.autoAccelerate,
                    driftChargeRatio = hudState.driftChargeRatio,
                    sparkTier = hudState.sparkTier,
                    opacity = profile.controlsOpacity,
                    buttonScale = profile.controlsScale,
                    heldItem = hudState.heldItem,
                    isRouletteSpinning = hudState.isRouletteSpinning,
                    rouletteDisplayItem = hudState.rouletteDisplayItem,
                    showRocketPedal = hudState.phase != com.example.game.RacePhase.RACING ||
                        hudState.raceTimeMs < 1500L,
                    modifier = Modifier.hudZone(actionsZoneEnum)
                )

                CountdownOverlay(
                    countdownNumber = hudState.countdownNumber,
                    showRocketStartTip = hudState.showRocketStartTip,
                    isWrongWay = hudState.isWrongWay,
                    bannerText = hudState.bannerText,
                    dnfCountdownSec = hudState.dnfCountdownSec,
                    modifier = Modifier.hudZone(HudZone.CENTER)
                )
            }

            if (pauseMenuOpen) {
                PauseOverlay(
                    trackName = Tracks.byId(hudState.trackId).name,
                    lap = hudState.lap,
                    totalLaps = hudState.totalLaps,
                    standings = sorted,
                    isMultiplayer = isMultiplayer,
                    canRestart = !isMultiplayer || isHost,
                    onResume = { setPaused(false) },
                    onPhoto = {
                        pauseMenuOpen = false
                        photoModeActive = true
                    },
                    onRestart = {
                        setPaused(false)
                        onRestartRace()
                    },
                    onSettings = onOpenSettings,
                    onLeave = { confirmLeave = true }
                )
            }

            if (confirmLeave) {
                DRDialog(
                    kicker = if (isMultiplayer) "LAN race in progress" else "Leave race",
                    title = "Leave this race?",
                    body = if (isMultiplayer) "A bot takes your kart and you won't get finish rewards. Other players keep racing."
                    else "Your progress in this race will be lost.",
                    confirmText = "Leave",
                    confirmIcon = Icons.AutoMirrored.Rounded.Logout,
                    onConfirm = {
                        confirmLeave = false
                        setPaused(false)
                        onQuitToMenu()
                    },
                    dismissText = "Stay",
                    onDismiss = { confirmLeave = false },
                    destructive = true,
                    testTag = "dialog_leave_race"
                )
            }
        }
    }
}

@Composable
private fun PauseOverlay(
    trackName: String,
    lap: Int,
    totalLaps: Int,
    standings: List<RacerStanding>,
    isMultiplayer: Boolean,
    canRestart: Boolean,
    onResume: () -> Unit,
    onPhoto: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onLeave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DR.Scrim)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 36.dp, vertical = 16.dp)
            .testTag("pause_overlay")
    ) {
        Column(Modifier.align(Alignment.TopStart).width(270.dp)) {
            DRLabel("${trackName} · Lap ${lap.coerceAtMost(totalLaps)}/$totalLaps", color = DR.Amber)
            Text("PAUSED", style = DRType.Display.copy(fontSize = 44.sp, lineHeight = 46.sp), color = DR.TextHi, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            DRButton(
                text = "Resume",
                icon = Icons.Rounded.PlayArrow,
                onClick = onResume,
                size = DRButtonSize.L,
                modifier = Modifier.fillMaxWidth(),
                testTag = "btn_pause_resume"
            )
            Spacer(Modifier.height(6.dp))
            if (!isMultiplayer) PauseRow(Icons.Rounded.CameraAlt, "Photo mode", DR.TextHi, "btn_pause_photo_mode", onPhoto)
            if (canRestart) PauseRow(Icons.Rounded.RestartAlt, "Restart race", DR.TextHi, "btn_pause_restart", onRestart)
            PauseRow(Icons.Rounded.Tune, "Controls & settings", DR.TextHi, "btn_pause_settings", onSettings)
            PauseRow(Icons.AutoMirrored.Rounded.Logout, "Leave race", DR.Bad, "btn_pause_quit", onLeave)
        }
        if (isMultiplayer) {
            Text(
                "LAN race keeps running while this menu is open.",
                style = DRType.Caption,
                color = DR.TextLo,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
        if (standings.isNotEmpty()) {
            DRPanel(
                modifier = Modifier.align(Alignment.TopEnd).width(260.dp),
                padding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                DRLabel("Standings", modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
                standings.take(8).forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(if (r.isLocal) DR.YouWash else Color.Transparent)
                            .drawBehind { if (r.isLocal) drawRect(DR.You, size = Size(3.dp.toPx(), size.height)) }
                            .padding(start = 14.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("${r.position}", style = DRType.Title.copy(fontSize = 19.sp), color = DR.podium(r.position), modifier = Modifier.width(18.dp))
                        KartThumb(r.bodyType, Modifier.size(width = 36.dp, height = 24.dp))
                        Text(r.name, style = DRType.BodyStrong.copy(fontSize = 12.5.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseRow(icon: ImageVector, text: String, color: Color, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind { drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = DRType.BodyStrong, color = color)
    }
}

/**
 * Part H4: Saves a souvenir race photo to Pictures/DeskRacer via MediaStore (zero permission needed on API 29+).
 */
private fun saveDeskRacerPhotoToMediaStore(
    context: Context,
    trackName: String,
    playerName: String
): String? {
    return try {
        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, 720f,
                intArrayOf(0xFF0B1020.toInt(), 0xFF1A2547.toInt(), 0xFF3B2416.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, 1280f, 720f, bgPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB547.toInt()
            textSize = 48f
            isFakeBoldText = true
        }
        canvas.drawText("DESK RACER LAN • $trackName", 64f, 620f, textPaint)
        textPaint.color = 0xFFFFFFFF.toInt()
        textPaint.textSize = 28f
        canvas.drawText("Driver: $playerName", 64f, 664f, textPaint)

        val filename = "DeskRacer_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DeskRacer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        bmp.recycle()
        uri.toString()
    } catch (e: Exception) {
        null
    }
}
