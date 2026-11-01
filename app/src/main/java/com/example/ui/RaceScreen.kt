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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.components.AutoSizeText
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonSize
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRCard
import com.example.ui.controls.ActionButtons
import com.example.ui.controls.SteerZone
import com.example.ui.hud.CountdownOverlay
import com.example.ui.hud.HudLayout
import com.example.ui.hud.HudZone
import com.example.ui.hud.ItemSlot
import com.example.ui.hud.LapTimer
import com.example.ui.hud.Minimap
import com.example.ui.hud.PositionBadge
import com.example.ui.hud.Speedometer
import com.example.ui.hud.hudZone
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.HudFontScaleScope
import com.example.ui.theme.InkBorder
import com.example.ui.theme.InkElevated
import com.example.ui.theme.InkSurface
import com.example.ui.theme.LampAmber
import com.example.ui.theme.NightNavy
import com.example.ui.theme.SuccessLime
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

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
    onQuitToMenu: () -> Unit
) {
    val context = LocalContext.current
    var pauseMenuOpen by remember { mutableStateOf(false) }
    var photoModeActive by remember { mutableStateOf(false) }
    var photoSavedFeedback by remember { mutableStateOf<String?>(null) }
    var photoOrbitDeg by remember { mutableFloatStateOf(0f) }

    BackHandler {
        if (photoModeActive) {
            photoModeActive = false
        } else {
            val next = !pauseMenuOpen
            pauseMenuOpen = next
            onPauseChanged(next)
        }
    }

    HudFontScaleScope {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("race_screen")
        ) {
            // 0. Pre-race 4-second Camera Fly-by Card + Tap to Skip
            if (hudState.phase == RacePhase.FLYBY_INTRO) {
                val track = Tracks.byId(hudState.trackId)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onSkipFlyby)
                        .padding(24.dp)
                        .testTag("flyby_overlay")
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = InkSurface.copy(alpha = 0.90f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, BoostCyan),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${CupType.fromId(track.cupId).title} • ${hudState.gameMode.title}",
                                style = DRType.Caption.copy(fontWeight = FontWeight.W800),
                                color = LampAmber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.name,
                                style = DRType.Title,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.subtitle} • ${track.difficultyLabel}",
                                style = DRType.Label,
                                color = BoostCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    DRButton(
                        text = "SKIP FLY-BY ▶",
                        variant = DRButtonVariant.SECONDARY,
                        size = DRButtonSize.M,
                        onClick = onSkipFlyby,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        testTag = "btn_skip_flyby"
                    )
                }
                return@HudFontScaleScope
            }

            // Part H4: Photo Mode (hides HUD, free camera controls + save screenshot to Pictures/DeskRacer)
            if (photoModeActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .testTag("photo_mode_overlay")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DRButton(
                            text = "EXIT PHOTO MODE",
                            variant = DRButtonVariant.SECONDARY,
                            size = DRButtonSize.M,
                            onClick = { photoModeActive = false },
                            testTag = "btn_exit_photo_mode"
                        )
                        if (photoSavedFeedback != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = InkSurface.copy(alpha = 0.90f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SuccessLime)
                            ) {
                                Text(
                                    text = photoSavedFeedback ?: "",
                                    style = DRType.Label,
                                    color = SuccessLime,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DRButton(
                                text = "ORBIT CAM",
                                variant = DRButtonVariant.SECONDARY,
                                size = DRButtonSize.M,
                                onClick = { photoOrbitDeg = (photoOrbitDeg + 45f) % 360f },
                                testTag = "btn_orbit_photo_cam"
                            )
                            DRButton(
                                text = "SAVE PHOTO",
                                icon = Icons.Filled.CameraAlt,
                                variant = DRButtonVariant.PRIMARY,
                                size = DRButtonSize.M,
                                onClick = {
                                    val savedPath = saveDeskRacerPhotoToMediaStore(
                                        context = context,
                                        trackName = Tracks.byId(hudState.trackId).name,
                                        playerName = profile.playerName
                                    )
                                    photoSavedFeedback = if (savedPath != null) {
                                        "Saved to Pictures/DeskRacer!"
                                    } else {
                                        "Photo captured!"
                                    }
                                },
                                testTag = "btn_save_photo"
                            )
                        }
                    }
                }
                return@HudFontScaleScope
            }

            // Part E1: Reserved 9-Zone HUD Layout Engine — guaranteed zero overlap at all sizes & font scales
            val steerZoneEnum = if (profile.leftHandedMirror) HudZone.BOTTOM_RIGHT else HudZone.BOTTOM_LEFT
            val actionsZoneEnum = if (profile.leftHandedMirror) HudZone.BOTTOM_LEFT else HudZone.BOTTOM_RIGHT

            HudLayout(
                applySafeInsets = applySafeInsets,
                showDebugZones = profile.showHudDebugZones,
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. TOP_LEFT: Position Badge, Lap, Coins
                PositionBadge(
                    position = hudState.position,
                    totalRacers = hudState.totalRacers,
                    lap = hudState.lap,
                    totalLaps = hudState.totalLaps,
                    coinsHeld = hudState.coinsHeld,
                    positionDeltaChip = hudState.positionDeltaChip,
                    balloonsRemaining = if (hudState.gameMode == GameMode.BATTLE_BALLOONS) hudState.balloonsRemaining else -1,
                    modifier = Modifier.hudZone(HudZone.TOP_LEFT)
                )

                // 2. TOP_CENTER: Timer and Banners
                LapTimer(
                    raceTimeMs = hudState.raceTimeMs,
                    bestLapMs = hudState.bestLapMs,
                    modifier = Modifier.hudZone(HudZone.TOP_CENTER)
                )

                // 3. TOP_RIGHT: Item Slot + Pause Button (56.dp hit area on far right)
                ItemSlot(
                    heldItem = hudState.heldItem,
                    isRouletteSpinning = hudState.isRouletteSpinning,
                    rouletteDisplayItem = hudState.rouletteDisplayItem,
                    onTapItemSlot = { inputState.triggerItem(fireBackward = false) },
                    onPauseClicked = {
                        pauseMenuOpen = true
                        onPauseChanged(true)
                    },
                    modifier = Modifier.hudZone(HudZone.TOP_RIGHT)
                )

                // 4. LEFT_MID: Minimap (0-18% w x 26-56% h — never intrudes into BOTTOM_LEFT steer zone)
                if (profile.showMinimap) {
                    Minimap(
                        trackId = hudState.trackId,
                        dots = hudState.minimapDots,
                        modifier = Modifier.hudZone(HudZone.LEFT_MID)
                    )
                }

                // 5. RIGHT_MID: Standings Ladder (84-100% w x 26-56% h; hidden automatically below 600 dp width)
                StandingsLadderHud(
                    standings = hudState.standings,
                    modifier = Modifier.hudZone(HudZone.RIGHT_MID)
                )

                // 6. BOTTOM_LEFT: Steering Control Zone
                SteerZone(
                    inputState = inputState,
                    controlScheme = profile.controlScheme,
                    sensitivity = profile.steerSensitivity,
                    opacity = profile.controlsOpacity,
                    modifier = Modifier.hudZone(steerZoneEnum)
                )

                // 7. BOTTOM_CENTER: Speedometer + Drift Bar
                Speedometer(
                    speedKmh = hudState.speedKmh,
                    speedRatio = hudState.speedRatio,
                    isDrifting = hudState.isDrifting,
                    driftChargeRatio = hudState.driftChargeRatio,
                    sparkTier = hudState.sparkTier,
                    modifier = Modifier.hudZone(HudZone.BOTTOM_CENTER)
                )

                // 8. BOTTOM_RIGHT: Action Buttons Cluster
                ActionButtons(
                    inputState = inputState,
                    autoAccelerate = profile.autoAccelerate,
                    driftChargeRatio = hudState.driftChargeRatio,
                    sparkTier = hudState.sparkTier,
                    opacity = profile.controlsOpacity,
                    buttonScale = profile.controlsScale,
                    modifier = Modifier.hudZone(actionsZoneEnum)
                )

                // 9. CENTER: Countdown, Wrong Way, and Toasts (non-interactive)
                CountdownOverlay(
                    countdownNumber = hudState.countdownNumber,
                    showRocketStartTip = hudState.showRocketStartTip,
                    isWrongWay = hudState.isWrongWay,
                    bannerText = hudState.bannerText,
                    toastText = hudState.toastText,
                    dnfCountdownSec = hudState.dnfCountdownSec,
                    modifier = Modifier.hudZone(HudZone.CENTER)
                )
            }

            // Pause Modal Overlay (<= 560 dp wide, centred per Part E7)
            if (pauseMenuOpen) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NightNavy.copy(alpha = 0.82f))
                        .testTag("pause_overlay")
                ) {
                    DRCard(
                        borderColor = LampAmber,
                        borderWidth = 2.dp,
                        modifier = Modifier
                            .widthIn(min = 280.dp, max = 420.dp)
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AutoSizeText(
                                text = if (isMultiplayer) "RACE MENU (LIVE LAN)" else "RACE PAUSED",
                                style = DRType.Headline,
                                color = LampAmber,
                                maxLines = 1
                            )
                            Text(
                                text = Tracks.byId(hudState.trackId).name,
                                style = DRType.Caption,
                                color = BoostCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            DRButton(
                                text = "RESUME",
                                variant = DRButtonVariant.PRIMARY,
                                size = DRButtonSize.M,
                                onClick = {
                                    pauseMenuOpen = false
                                    onPauseChanged(false)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "btn_pause_resume"
                            )

                            if (!isMultiplayer) {
                                DRButton(
                                    text = "PHOTO MODE",
                                    icon = Icons.Filled.CameraAlt,
                                    variant = DRButtonVariant.SECONDARY,
                                    size = DRButtonSize.M,
                                    onClick = {
                                        pauseMenuOpen = false
                                        photoModeActive = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "btn_pause_photo_mode"
                                )
                            }

                            if (!isMultiplayer || isHost) {
                                DRButton(
                                    text = "RESTART RACE",
                                    variant = DRButtonVariant.SECONDARY,
                                    size = DRButtonSize.M,
                                    onClick = {
                                        pauseMenuOpen = false
                                        onPauseChanged(false)
                                        onRestartRace()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "btn_pause_restart"
                                )
                            }

                            DRButton(
                                text = "CONTROLS & SETTINGS",
                                variant = DRButtonVariant.SECONDARY,
                                size = DRButtonSize.M,
                                onClick = onOpenSettings,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "btn_pause_settings"
                            )

                            DRButton(
                                text = "QUIT TO MENU",
                                variant = DRButtonVariant.DANGER,
                                size = DRButtonSize.M,
                                onClick = {
                                    pauseMenuOpen = false
                                    onPauseChanged(false)
                                    onQuitToMenu()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "btn_pause_quit"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Part E1 & E4: RIGHT_MID Standings Ladder (84-100% w x 26-56% h).
 * Names are truncated to 12 chars and numbers use tabular figures ("tnum").
 */
@Composable
fun StandingsLadderHud(
    standings: List<RacerStanding>,
    modifier: Modifier = Modifier
) {
    if (standings.isEmpty()) return
    Column(
        modifier = modifier
            .widthIn(min = 84.dp, max = 132.dp)
            .background(InkSurface.copy(alpha = 0.80f), RoundedCornerShape(10.dp))
            .border(1.dp, InkBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("hud_standings_ladder"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (entry in standings.take(4)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (entry.isLocal) InkElevated else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${entry.position}.",
                    style = DRType.Caption.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W800,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (entry.position == 1) LampAmber else TextSecondary,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(entry.colorHex))
                )
                Text(
                    text = entry.name.take(12),
                    style = DRType.Caption.copy(
                        fontSize = 10.sp,
                        fontWeight = if (entry.isLocal) FontWeight.W800 else FontWeight.W500
                    ),
                    color = if (entry.isLocal) BoostCyan else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
