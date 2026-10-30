package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileAndSettings
import com.example.game.HudState
import com.example.game.InputState
import com.example.ui.controls.ActionButtons
import com.example.ui.controls.SteerZone
import com.example.ui.hud.CountdownOverlay
import com.example.ui.hud.ItemSlot
import com.example.ui.hud.LapTimer
import com.example.ui.hud.Minimap
import com.example.ui.hud.PositionBadge
import com.example.ui.hud.Speedometer
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.JoinBlue
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface

@Composable
fun RaceScreen(
    hudState: HudState,
    profile: UserProfileAndSettings,
    inputState: InputState,
    isMultiplayer: Boolean,
    isHost: Boolean,
    onPauseChanged: (Boolean) -> Unit,
    onRestartRace: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuitToMenu: () -> Unit
) {
    var pauseMenuOpen by remember { mutableStateOf(false) }

    BackHandler {
        val next = !pauseMenuOpen
        pauseMenuOpen = next
        onPauseChanged(next)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .testTag("race_screen")
    ) {
        // 1. Left 45% Full-Height Steering Zone (Touch writes directly to InputState)
        Row(modifier = Modifier.fillMaxSize()) {
            SteerZone(
                inputState = inputState,
                controlScheme = profile.controlScheme,
                sensitivity = profile.steerSensitivity,
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxSize()
            )
            Spacer(modifier = Modifier.weight(0.58f))
        }

        // 2. HUD Elements & Right-Thumb Action Buttons (16dp margins)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top-Left: Position Badge + Lap Counter
            PositionBadge(
                position = hudState.position,
                totalRacers = hudState.totalRacers,
                lap = hudState.lap,
                totalLaps = hudState.totalLaps,
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Top-Centre: Race Timer mm:ss.mmm + Best Lap
            LapTimer(
                raceTimeMs = hudState.raceTimeMs,
                bestLapMs = hudState.bestLapMs,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Top-Right: Item Slot (Roulette animation) + Pause Button
            ItemSlot(
                heldItem = hudState.heldItem,
                isRouletteSpinning = hudState.isRouletteSpinning,
                rouletteDisplayItem = hudState.rouletteDisplayItem,
                onTapItemSlot = { inputState.triggerItem(fireBackward = false) },
                onPauseClicked = {
                    pauseMenuOpen = true
                    onPauseChanged(true)
                },
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // Left-Middle: 120dp Minimap (can be hidden in settings)
            if (profile.showMinimap) {
                Minimap(
                    trackId = hudState.trackId,
                    dots = hudState.minimapDots,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Centre Overlays: 3-2-1-GO Countdown, WRONG WAY, Banners, Toasts
            CountdownOverlay(
                countdownNumber = hudState.countdownNumber,
                showRocketStartTip = hudState.showRocketStartTip,
                isWrongWay = hudState.isWrongWay,
                bannerText = hudState.bannerText,
                toastText = hudState.toastText,
                modifier = Modifier.align(Alignment.Center)
            )

            // Bottom-Centre: Speedometer Arc + Blue/Orange/Purple Drift-Charge Bar
            Speedometer(
                speedKmh = hudState.speedKmh,
                speedRatio = hudState.speedRatio,
                isDrifting = hudState.isDrifting,
                driftChargeRatio = hudState.driftChargeRatio,
                sparkTier = hudState.sparkTier,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Bottom-Right: Right-Thumb Action Buttons (ITEM, BRAKE, DRIFT, GAS)
            ActionButtons(
                inputState = inputState,
                autoAccelerate = profile.autoAccelerate,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // 3. Pause Modal Overlay
        if (pauseMenuOpen) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC090D16))
                    .testTag("pause_overlay")
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SlateAbyss,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, SkyCyan),
                    modifier = Modifier.width(340.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isMultiplayer) "RACE MENU (LIVE LAN)" else "RACE PAUSED",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = SkyCyan
                        )

                        Button(
                            onClick = {
                                pauseMenuOpen = false
                                onPauseChanged(false)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HostEmerald),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_pause_resume")
                        ) {
                            Text("RESUME", fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold)
                        }

                        if (!isMultiplayer || isHost) {
                            Button(
                                onClick = {
                                    pauseMenuOpen = false
                                    onPauseChanged(false)
                                    onRestartRace()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DriftAmber),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_pause_restart")
                            ) {
                                Text("RESTART RACE", fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, color = SlateAbyss)
                            }
                        }

                        Button(
                            onClick = {
                                pauseMenuOpen = false
                                onPauseChanged(false)
                                onOpenSettings()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JoinBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_pause_controls")
                        ) {
                            Text("CONTROLS & SETTINGS", fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                pauseMenuOpen = false
                                onPauseChanged(false)
                                onQuitToMenu()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ArcadeCrimson),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_pause_quit")
                        ) {
                            Text("QUIT TO MENU", fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
