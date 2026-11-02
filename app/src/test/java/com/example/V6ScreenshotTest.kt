package com.example

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.FakeAdGateway
import com.example.data.FakeBillingGateway
import com.example.data.Prefs
import com.example.data.UserProfileAndSettings
import com.example.game.GameMode
import com.example.game.HudState
import com.example.game.InputState
import com.example.game.ItemType
import com.example.game.KartBodyType
import com.example.game.MinimapDot
import com.example.game.RacePhase
import com.example.game.RacerStanding
import com.example.game.Tracks
import com.example.net.DiscoveredRoomBeacon
import com.example.net.LobbySlotInfo
import com.example.render.AssetRepository
import com.example.ui.GarageScreen
import com.example.ui.HostLobbyScreen
import com.example.ui.HowToPlayOverlay
import com.example.ui.JoinScreen
import com.example.ui.MainMenuScreen
import com.example.ui.RaceScreen
import com.example.ui.ResultsScreen
import com.example.ui.SettingsScreen
import com.example.ui.ShopScreen
import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.ItemMode
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every v6 screen at a 915x412 dp landscape phone (xhdpi) and writes PNGs to
 * app/build/screens for visual review. Backgrounds stand in for the live 3D scene.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w915dp-h412dp-land-xhdpi", sdk = [34])
open class V6ScreenshotTest {
    /** Device profile label + system font scale (the app theme clamps it). */
    open val profileTag: String = "L"
    open val systemFontScale: Float = 1.0f

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val profile = UserProfileAndSettings(playerName = "ADITYA", coinsBalance = 2450, gemsBalance = 120, deskPassXp = 2700)

    private val standings = listOf(
        RacerStanding(1, 1, "MEERA", 0xFF2DE2E6, KartBodyType.entries[2], true, false, 221208, 72090),
        RacerStanding(2, 2, "ROHAN", 0xFFA3E635, KartBodyType.entries[1], true, false, 222911, 72400),
        RacerStanding(0, 3, "ADITYA", 0xFFFF3D7F, KartBodyType.entries[5], true, true, 224050, 72090, isNewRecord = true),
        RacerStanding(3, 4, "VOLT", 0xFFFFB547, KartBodyType.entries[0], false, false, 227322, 74100),
        RacerStanding(4, 5, "KABIR", 0xFFA855F7, KartBodyType.entries[3], true, false, 231090, 75200),
        RacerStanding(5, 6, "PIXEL", 0xFF3B82F6, KartBodyType.entries[4], false, false, 0, 0, isDnf = true)
    )

    private fun dots(): List<MinimapDot> {
        val s = Tracks.byId(0).samples
        if (s.isEmpty()) return emptyList()
        return listOf(10, 40, 70, 95, 120, 150).mapIndexed { i, idx ->
            val p = s[(idx * s.size / 170).coerceIn(0, s.size - 1)].pos
            MinimapDot(i, p.x, p.z, 0xFFFFFFFF, i == 2)
        }
    }

    /** Text-vs-text overlaps, clipped/ellipsized text and off-screen text, from the unmerged semantics tree. */
    private fun visualQa(w: Float, h: Float): List<String> {
        val nodes = rule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Text),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        data class T(val text: String, val r: androidx.compose.ui.geometry.Rect, val clipped: Boolean)
        val ts = nodes.mapNotNull { n ->
            val text = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            val r = n.boundsInRoot
            if (text.isBlank() || r.width < 1f || r.height < 1f) return@mapNotNull null
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val clipped = layouts.any { it.hasVisualOverflow || (it.lineCount > 0 && it.isLineEllipsized(it.lineCount - 1)) }
            T(text, r, clipped)
        }
        val out = mutableListOf<String>()
        for (i in ts.indices) for (j in i + 1 until ts.size) {
            val a = ts[i]; val b = ts[j]
            val inter = a.r.intersect(b.r)
            if (inter.width > 3f && inter.height > 3f) out += "OVERLAP '${a.text.take(30)}' x '${b.text.take(30)}'"
        }
        ts.filter { it.clipped }.forEach { out += "CLIPPED '${it.text.take(40)}'" }
        ts.filter { it.r.left < -2f || it.r.top < -2f || it.r.right > w + 2f || it.r.bottom > h + 2f }.forEach { out += "OFFSCREEN '${it.text.take(30)}'" }
        return out
    }

    private fun shot(name: String, backdrop: String? = "textures/backdrops/room_workshop.jpg", qa: Boolean = true, before: () -> Unit = {}, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
          val baseDensity = androidx.compose.ui.platform.LocalDensity.current
          androidx.compose.runtime.CompositionLocalProvider(
              androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density, systemFontScale)
          ) {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF2A2622))) {
                    if (backdrop != null) {
                        val ctx = LocalContext.current
                        val bmp = AssetRepository.loadUiBitmapStatic(ctx, backdrop)
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    content()
                }
            }
          }
        }
        rule.mainClock.advanceTimeBy(400)
        before()
        rule.mainClock.advanceTimeBy(1600)
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val dir = File("build/screens").apply { mkdirs() }
        FileOutputStream(File(dir, "${profileTag}_$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (qa) {
            val issues = visualQa(view.width.toFloat(), view.height.toFloat())
            File(dir, "${profileTag}_$name.qa.txt").writeText(issues.joinToString("\n"))
            val hard = issues.filter { it.startsWith("OVERLAP") || it.startsWith("OFFSCREEN") }
            org.junit.Assert.assertTrue("${profileTag}_$name: " + hard.joinToString(" | ").take(700), hard.isEmpty())
        }
    }

    @Test
    fun s01_mainMenu() = shot("01_main_menu", "textures/backdrops/room_studio.jpg") {
        MainMenuScreen(profile = profile, onQuickRace = {}, onHostRace = {}, onJoinRace = {}, onOpenGarage = {}, onOpenSettings = {}, onOpenHowToConnect = {})
    }

    @Test
    fun s02_raceSetup() = shot("02_race_setup", before = { rule.onNodeWithTag("btn_open_race_setup").performClick() }) {
        MainMenuScreen(profile = profile, onQuickRace = {}, onHostRace = {}, onJoinRace = {}, onOpenGarage = {}, onOpenSettings = {}, onOpenHowToConnect = {})
    }

    @Test
    fun s03_garageBody() = shot("03_garage_body") {
        GarageScreen(profile = profile, onNameChanged = {}, onColorSelected = {}, onBodySelected = {}, onBack = {})
    }

    @Test
    fun s04_garagePaint() = shot("04_garage_paint", before = { rule.onNodeWithTag("garage_tab_1").performClick() }) {
        GarageScreen(profile = profile, onNameChanged = {}, onColorSelected = {}, onBodySelected = {}, onBack = {})
    }

    @Test
    fun s05_hostLobby() = shot("05_host_lobby", "textures/backdrops/room_lobby.jpg") {
        HostLobbyScreen(
            roomCode = "4817", hostIpAddress = "192.168.43.1",
            slots = listOf(
                LobbySlotInfo(0, "ADITYA", 0, KartBodyType.entries[5], true, true, 0),
                LobbySlotInfo(1, "ROHAN", 3, KartBodyType.entries[1], true, true, 18),
                LobbySlotInfo(2, "MEERA", 2, KartBodyType.entries[2], true, true, 22),
                LobbySlotInfo(3, "KABIR", 5, KartBodyType.entries[3], true, false, 31)
            ),
            selectedTrackId = 1, selectedLaps = 3, cpuFillEnabled = true, itemMode = ItemMode.NORMAL,
            canForceStart = true, onSelectTrack = {}, onSelectLaps = {}, onToggleCpuFill = {}, onCycleItemMode = {},
            onKickSlot = {}, onStartRace = {}, onBack = {}
        )
    }

    @Test
    fun s06_join() = shot("06_join", "textures/backdrops/room_lobby.jpg") {
        JoinScreen(
            discoveredRooms = listOf(
                DiscoveredRoomBeacon(1.toByte(), "4817", "ADI", "192.168.43.1", 47778, 1, 4, 8, false),
                DiscoveredRoomBeacon(1.toByte(), "2290", "ROHAN", "192.168.43.7", 47778, 5, 2, 8, false, pingMs = 96),
                DiscoveredRoomBeacon(1.toByte(), "7731", "HOSTEL B", "192.168.43.9", 47778, 6, 8, 8, false)
            ),
            defaultGatewayIp = "192.168.43.1", statusMessage = "Listening on UDP 47777", isConnectedToLobby = false,
            roomCode = "", trackId = 0, laps = 3, slots = emptyList(), isClientReady = false,
            onConnectIp = {}, onToggleReady = {}, onBack = {}
        )
    }

    private val raceHud = HudState(
        phase = RacePhase.RACING, gameMode = GameMode.QUICK_RACE, trackId = 0, position = 3, totalRacers = 6, lap = 2, totalLaps = 3,
        raceTimeMs = 84381, coinsHeld = 7, speedKmh = 42, speedRatio = 0.7f, isDrifting = true, driftChargeRatio = 0.62f, sparkTier = 1,
        heldItem = ItemType.MARBLE, toastText = "You hit ROHAN", standings = emptyList(), countdownNumber = -1
    )

    @Test
    fun s07_hudRace() = shot("07_hud_race", "textures/backdrops/room_studio.jpg") {
        RaceScreen(raceHud.copy(minimapDots = dots(), standings = standings), profile, InputState(), false, true, onPauseChanged = {}, onRestartRace = {}, onOpenSettings = {}, onQuitToMenu = {})
    }

    @Test
    fun s08_hudCountdown() = shot("08_hud_countdown", "textures/backdrops/room_studio.jpg") {
        RaceScreen(raceHud.copy(phase = RacePhase.COUNTDOWN, countdownNumber = 2, showRocketStartTip = true, toastText = null, heldItem = ItemType.NONE, minimapDots = dots()), profile, InputState(), false, true, onPauseChanged = {}, onRestartRace = {}, onOpenSettings = {}, onQuitToMenu = {})
    }

    @Test
    fun s09_pause() = shot("09_pause", "textures/backdrops/room_studio.jpg", before = { rule.onNodeWithTag("hud_pause_button").performClick() }) {
        RaceScreen(raceHud.copy(standings = standings, minimapDots = dots()), profile, InputState(), false, true, onPauseChanged = {}, onRestartRace = {}, onOpenSettings = {}, onQuitToMenu = {})
    }

    @Test
    fun s10_results() = shot("10_results", "textures/backdrops/room_studio.jpg") {
        ResultsScreen(trackName = "Study Desk", standings = standings, isMultiplayer = false, isHost = true, coinsEarnedThisRace = 120, xpEarnedThisRace = 110, onRematch = {}, onBackToLobby = {}, onMainMenu = {})
    }

    @Test
    fun s11_settings() = shot("11_settings", null) {
        SettingsScreen(
            settings = profile, onControlSchemeChanged = {}, onSensitivityChanged = {}, onAutoAccelerateChanged = {}, onSmartSteeringChanged = {},
            onShowMinimapChanged = {}, onSoundChanged = {}, onHapticsChanged = {}, onGraphicsChanged = {}, onDifficultyChanged = {}, onBack = {}
        )
    }

    @Test
    fun s12_shop() = shot("12_shop", null) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ShopScreen(profile = profile, prefs = Prefs(ctx), billingGateway = FakeBillingGateway(), adGateway = FakeAdGateway(), onBack = {})
    }

    @Test
    fun s13_howToPlay() = shot("13_how_to_play", "textures/backdrops/room_studio.jpg") {
        HowToPlayOverlay(onDismiss = {})
    }

    @Test
    fun s14_leaveDialog() = shot(
        "14_leave_dialog", "textures/backdrops/room_studio.jpg", qa = false,
        before = {
            rule.onNodeWithTag("hud_pause_button").performClick()
            rule.mainClock.advanceTimeBy(400)
            rule.onNodeWithTag("btn_pause_quit").performClick()
        }
    ) {
        RaceScreen(raceHud.copy(minimapDots = dots()), profile, InputState(), true, false, onPauseChanged = {}, onRestartRace = {}, onOpenSettings = {}, onQuitToMenu = {})
    }

    @Test
    fun s16_garageLivery() = shot("16_garage_livery", before = { rule.onNodeWithTag("garage_tab_2").performClick() }) {
        GarageScreen(profile = profile, onNameChanged = {}, onColorSelected = {}, onBodySelected = {}, onBack = {})
    }

    @Suppress("unused")
    private val keep = listOf(CcClass.CC_150, CpuDifficulty.PRO)
}

/** Same screens on a compact 20:9 phone at large display + font size (the reported device). */
@Config(qualifiers = "w800dp-h360dp-land-xxhdpi", sdk = [34])
class V6ScreenshotSmallTest : V6ScreenshotTest() {
    override val profileTag: String = "S"
    override val systemFontScale: Float = 1.3f
}
