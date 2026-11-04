package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ShopCatalog
import com.example.data.UserProfileAndSettings
import com.example.game.GameConstants
import com.example.game.KartBodyType
import com.example.ui.components.DRBadge
import com.example.ui.components.DRBadgeKind
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonSize
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRCoin
import com.example.ui.components.DRDialog
import com.example.ui.components.DRKerbStrip
import com.example.ui.components.DRLabel
import com.example.ui.components.DRPanel
import com.example.ui.components.DRScreenHeader
import com.example.ui.components.DRSegmented
import com.example.ui.components.DRStatBar
import com.example.ui.components.DRTabs
import com.example.ui.components.DRTextField
import com.example.ui.components.DRWallet
import com.example.ui.components.KartThumb
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType
import java.util.Locale

private val KART_COLOR_NAMES = listOf(
    "Hot Pink", "Lamp Amber", "Boost Cyan", "Lime", "Coral", "Hyper Purple", "Cobalt", "Chalk White",
    "Racing Red", "Mint", "Solar Gold", "Tangerine", "Magenta", "Aqua", "Violet", "Titanium"
)
private val LIVERY_NAMES = listOf("Solid", "Two-Tone", "Racing Stripes", "Fade")
private val LIVERY_DESC = listOf("One colour, full body", "Graphite lower body", "Twin stripes nose to tail", "Colour fades to a deep shade")
private val FINISH_NAMES = listOf("Gloss", "Matte", "Metal", "Pearl")

private fun KartBodyType.speedFrac() = ((topSpeedFactor - 0.8f) / 0.3f).coerceIn(0.05f, 1f)
private fun KartBodyType.accelFrac() = ((accelFactor - 0.8f) / 0.3f).coerceIn(0.05f, 1f)
private fun KartBodyType.handlingFrac() = ((handlingFactor - 0.8f) / 0.3f).coerceIn(0.05f, 1f)
private fun KartBodyType.weightFrac() = ((mass - 0.7f) / 0.65f).coerceIn(0.05f, 1f)
private fun fmt10(f: Float) = String.format(Locale.US, "%.1f", f * 10f)

fun bodyCoinPrice(body: KartBodyType): Int =
    ShopCatalog.cosmeticOffers.firstOrNull { it.unlocksBodyId == body.id }?.coinPrice
        ?: body.unlockCoins.takeIf { it > 0 }
        ?: 600

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
    onPreviewLoadout: (body: KartBodyType, colorIdx: Int, liveryIdx: Int, finishIdx: Int) -> Unit = { _, _, _, _ -> },
    onBuyBody: (KartBodyType) -> Boolean = { false },
    onBack: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var draftBody by remember { mutableStateOf(profile.kartBody) }
    var draftColor by remember { mutableIntStateOf(profile.kartColorIndex) }
    var draftLivery by remember { mutableIntStateOf(profile.kartLiveryIndex) }
    var draftFinish by remember { mutableIntStateOf(profile.paintFinishIndex) }
    var showDiscard by remember { mutableStateOf(false) }
    var hasDragged by remember { mutableStateOf(false) }

    val dirty = draftBody != profile.kartBody || draftColor != profile.kartColorIndex ||
        draftLivery != profile.kartLiveryIndex || draftFinish != profile.paintFinishIndex
    val draftLocked = draftBody.id !in profile.unlockedBodies
    val price = bodyCoinPrice(draftBody)

    fun preview(body: KartBodyType = draftBody, c: Int = draftColor, l: Int = draftLivery, f: Int = draftFinish) {
        draftBody = body; draftColor = c; draftLivery = l; draftFinish = f
        onPreviewLoadout(body, c, l, f)
    }

    fun resetDraft() {
        preview(profile.kartBody, profile.kartColorIndex, profile.kartLiveryIndex, profile.paintFinishIndex)
    }

    fun commit() {
        if (draftLocked) {
            if (!onBuyBody(draftBody)) return
        }
        if (draftBody != profile.kartBody) onBodySelected(draftBody)
        if (draftColor != profile.kartColorIndex) onColorSelected(draftColor)
        if (draftLivery != profile.kartLiveryIndex) onLiverySelected(draftLivery)
        if (draftFinish != profile.paintFinishIndex) onPaintFinishSelected(draftFinish)
    }

    fun handleBack() {
        if (dirty) showDiscard = true else onBack()
    }

    BackHandler(onBack = { if (showDiscard) showDiscard = false else handleBack() })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("garage_screen")
    ) {
        // Readability gradient: dark behind the info column and the panel, clear over the kart.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to DR.Bg.copy(alpha = 0.88f),
                        0.36f to DR.Bg.copy(alpha = 0.18f),
                        0.56f to DR.Bg.copy(alpha = 0.18f),
                        1f to DR.Bg.copy(alpha = 0.72f)
                    )
                )
        )
        // Turntable drag surface (behind the UI)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var lastVel = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { onRotateTurntable(0f, lastVel) },
                        onDragCancel = { onRotateTurntable(0f, 0f) }
                    ) { change, dx ->
                        change.consume()
                        hasDragged = true
                        val yaw = dx * 0.012f
                        lastVel = yaw * 60f
                        onRotateTurntable(yaw, 0f)
                    }
                }
                .testTag("garage_turntable_drag")
        )

        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)
        ) {
            // v6.1 layout: name/subtitle under the header (above the kart), stats in two columns at
            // the bottom-left (below the kart); the 3D kart sits in the clear band between them.
            val panelW = if (maxWidth < 860.dp) 316.dp else 344.dp
            val stageW = maxWidth - panelW - 12.dp
            val compact = maxHeight < 380.dp
            Row(Modifier.width(stageW), verticalAlignment = Alignment.CenterVertically) {
                DRScreenHeader(kicker = "Garage", title = "Customise", onBack = { handleBack() }, backTestTag = "btn_garage_back", modifier = Modifier.weight(1f))
                DRWallet(coins = profile.coinsBalance)
            }

            Column(Modifier.align(Alignment.TopStart).padding(top = 58.dp, start = 4.dp).width(stageW - 8.dp)) {
                com.example.ui.components.AutoSizeText(
                    text = draftBody.title.uppercase(),
                    style = DRType.Display.copy(fontSize = if (compact) 22.sp else 26.sp, lineHeight = 28.sp),
                    color = DR.TextHi,
                    maxLines = 1,
                    minFontSize = 16.sp
                )
                Text(
                    text = when (tab) {
                        1 -> "${KART_COLOR_NAMES.getOrElse(draftColor) { "Custom" }} · ${FINISH_NAMES[draftFinish.coerceIn(0, 3)].lowercase()}"
                        2 -> "${LIVERY_NAMES.getOrElse(draftLivery) { "Stock" }} livery"
                        else -> draftBody.subtitle
                    },
                    style = DRType.Caption,
                    color = DR.TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val eq = profile.kartBody
            val cmp = draftBody != eq
            val barW = ((stageW - 16.dp) / 2 - 112.dp).coerceIn(48.dp, 112.dp)
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .width(stageW)
                    .clip(DRShape.Card)
                    .background(DR.Bg.copy(alpha = 0.62f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    DRStatBar("Speed", draftBody.speedFrac(), fmt10(draftBody.speedFrac()), compare = if (cmp) eq.speedFrac() else null, barWidth = barW)
                    DRStatBar("Accel", draftBody.accelFrac(), fmt10(draftBody.accelFrac()), compare = if (cmp) eq.accelFrac() else null, barWidth = barW)
                }
                Column(Modifier.weight(1f)) {
                    DRStatBar("Handling", draftBody.handlingFrac(), fmt10(draftBody.handlingFrac()), compare = if (cmp) eq.handlingFrac() else null, barWidth = barW)
                    DRStatBar("Weight", draftBody.weightFrac(), fmt10(draftBody.weightFrac()), compare = if (cmp) eq.weightFrac() else null, barWidth = barW)
                }
            }

            // Right customisation panel
            DRPanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(panelW)
                    .fillMaxHeight(),
                padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            ) {
                DRTabs(
                    items = listOf("Body", "Paint", "Livery", "Driver"),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.fillMaxWidth(),
                    testTagPrefix = "garage_tab",
                    showIndex = panelW > 330.dp,
                    spacing = if (panelW > 330.dp) 18.dp else 14.dp
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                    when (tab) {
                        0 -> BodyGrid(profile, draftBody) { preview(body = it) }
                        1 -> PaintTab(draftColor, draftFinish, onColor = { preview(c = it) }, onFinish = { preview(f = it) })
                        2 -> LiveryTab(draftBody, draftLivery, draftColor) { preview(l = it) }
                        else -> DriverTab(profile.playerName, onNameChanged)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DRButton(
                        text = "Reset",
                        onClick = { resetDraft() },
                        variant = DRButtonVariant.GHOST,
                        icon = Icons.Rounded.RestartAlt,
                        enabled = dirty,
                        testTag = "btn_garage_reset"
                    )
                    when {
                        tab == 3 -> Spacer(Modifier.height(46.dp))
                        draftLocked -> DRButton(
                            text = "Buy · ${String.format(Locale.US, "%,d", price)}",
                            onClick = { commit() },
                            enabled = profile.coinsBalance >= price,
                            size = DRButtonSize.M,
                            testTag = "cta_primary"
                        )
                        else -> DRButton(
                            text = if (!dirty) "Equipped" else if (tab == 0) "Equip" else "Apply",
                            onClick = { commit() },
                            enabled = dirty,
                            icon = Icons.Rounded.Check,
                            size = DRButtonSize.M,
                            testTag = "cta_primary"
                        )
                    }
                }
            }
        }
        DRKerbStrip(Modifier.align(Alignment.BottomCenter))

        if (showDiscard) {
            DRDialog(
                kicker = "Unsaved changes",
                title = "Discard changes?",
                body = "Your new paint and body choices haven't been equipped yet.",
                confirmText = "Discard",
                onConfirm = {
                    showDiscard = false
                    resetDraft()
                    onBack()
                },
                dismissText = "Keep editing",
                onDismiss = { showDiscard = false },
                destructive = true,
                testTag = "dialog_garage_discard"
            )
        }
    }
}

@Composable
private fun BodyGrid(profile: UserProfileAndSettings, selected: KartBodyType, onPick: (KartBodyType) -> Unit) {
    val bodies = KartBodyType.entries
    Column(
        modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        bodies.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { body ->
                    val sel = body == selected
                    val locked = body.id !in profile.unlockedBodies
                    val equipped = body == profile.kartBody
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(DRShape.Card)
                            .background(if (sel) DR.AmberTint else DR.S2)
                            .border(if (sel) 1.5.dp else 1.dp, if (sel) DR.Amber else DR.Line, DRShape.Card)
                            .clickable(role = Role.RadioButton) { onPick(body) }
                            .testTag("garage_body_${body.id}")
                    ) {
                        KartThumb(
                            body = body,
                            locked = locked,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                                .width(86.dp)
                                .height(54.dp)
                        )
                        if (equipped) DRBadge("On", DRBadgeKind.AMBER, Modifier.align(Alignment.TopEnd).padding(6.dp))
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 7.dp, end = 6.dp)) {
                            com.example.ui.components.AutoSizeText(body.title, style = DRType.BodyStrong.copy(fontSize = 11.5.sp), color = DR.TextHi, minFontSize = 9.sp)
                            if (locked) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DRCoin(11.dp)
                                    Text(String.format(Locale.US, "%,d", bodyCoinPrice(body)), style = DRType.NumS.copy(fontSize = 12.sp), color = DR.Amber)
                                }
                            } else {
                                Text(if (equipped) "EQUIPPED" else "OWNED", style = DRType.NumS.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = DR.TextLo)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PaintTab(colorIdx: Int, finishIdx: Int, onColor: (Int) -> Unit, onFinish: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DRLabel("Primary · ")
            DRLabel(KART_COLOR_NAMES.getOrElse(colorIdx) { "Custom" }, color = DR.TextHi)
        }
        Spacer(Modifier.height(8.dp))
        GameConstants.KART_COLORS.toList().chunked(8).forEachIndexed { r, rowColors ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowColors.forEachIndexed { c, hex ->
                    val idx = r * 8 + c
                    val sel = idx == colorIdx
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.86f)
                            .clip(DRShape.Control)
                            .background(Color(hex))
                            .then(if (sel) Modifier.border(2.dp, DR.Amber, DRShape.Control) else Modifier)
                            .clickable(role = Role.RadioButton) { onColor(idx) }
                            .testTag("garage_color_$idx")
                    ) {
                        if (sel) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = DR.OnAmber, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DRLabel("Finish")
        Spacer(Modifier.height(8.dp))
        DRSegmented(
            options = FINISH_NAMES,
            selected = finishIdx.coerceIn(0, 3),
            onSelect = onFinish,
            modifier = Modifier.fillMaxWidth(),
            testTagPrefix = "garage_finish"
        )
    }
}

@Composable
private fun LiveryTab(body: KartBodyType, liveryIdx: Int, colorIdx: Int, onPick: (Int) -> Unit) {
    val paint = Color(GameConstants.KART_COLORS.getOrElse(colorIdx) { GameConstants.KART_COLORS[0] })
    val isToy = body.isDrm2
    Column(
        modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val count = if (isToy) 3 else LIVERY_NAMES.size
        for (i in 0 until count) {
            val sel = i == liveryIdx
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(DRShape.Card)
                    .background(if (sel) DR.AmberTint else DR.S2)
                    .border(if (sel) 1.5.dp else 1.dp, if (sel) DR.Amber else DR.Line, DRShape.Card)
                    .clickable(role = Role.RadioButton) { onPick(i) }
                    .padding(horizontal = 12.dp)
                    .testTag("garage_livery_$i"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiverySwatch(mode = if (isToy) 0 else i, paint = paint, modifier = Modifier.width(64.dp).height(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isToy) listOf("Stock", "Shifted", "Inverted")[i] else LIVERY_NAMES[i],
                        style = DRType.Title.copy(fontSize = 16.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isToy) "Hue variation" else LIVERY_DESC[i],
                        style = DRType.Caption, color = DR.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (sel) DRBadge("On", DRBadgeKind.AMBER)
            }
        }
    }
}

/** Little side-profile chip that previews the livery pattern in the chosen colour. */
@Composable
private fun LiverySwatch(mode: Int, paint: Color, modifier: Modifier = Modifier) {
    val lum = 0.299f * paint.red + 0.587f * paint.green + 0.114f * paint.blue
    val stripe = if (lum < 0.62f) DR.SwatchStripeLight else DR.SwatchStripeDark
    androidx.compose.foundation.Canvas(modifier.clip(DRShape.Control)) {
        val w = size.width; val h = size.height
        drawRect(DR.SwatchStage)
        val body = androidx.compose.ui.geometry.Rect(w * 0.06f, h * 0.30f, w * 0.94f, h * 0.78f)
        when (mode) {
            1 -> {
                drawRect(paint, body.topLeft, androidx.compose.ui.geometry.Size(body.width, body.height * 0.55f))
                drawRect(DR.SwatchGraphite, androidx.compose.ui.geometry.Offset(body.left, body.top + body.height * 0.55f), androidx.compose.ui.geometry.Size(body.width, body.height * 0.45f))
            }
            2 -> {
                drawRect(paint, body.topLeft, body.size)
                drawRect(stripe, androidx.compose.ui.geometry.Offset(body.left, body.top + body.height * 0.18f), androidx.compose.ui.geometry.Size(body.width, body.height * 0.13f))
                drawRect(stripe, androidx.compose.ui.geometry.Offset(body.left, body.top + body.height * 0.40f), androidx.compose.ui.geometry.Size(body.width, body.height * 0.13f))
            }
            3 -> drawRect(
                Brush.horizontalGradient(listOf(Color(paint.blue * 0.55f + 0.05f, paint.red * 0.25f + 0.04f, paint.green * 0.55f + 0.10f), paint), startX = body.left, endX = body.right),
                body.topLeft, body.size
            )
            else -> drawRect(paint, body.topLeft, body.size)
        }
        // cabin + wheels
        drawRect(DR.SwatchGlass, androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.14f), androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.18f))
        drawCircle(DR.SwatchTyre, radius = h * 0.15f, center = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.80f))
        drawCircle(DR.SwatchTyre, radius = h * 0.15f, center = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.80f))
    }
}

@Composable
private fun DriverTab(name: String, onNameChanged: (String) -> Unit) {
    Column {
        DRLabel("Driver name")
        Spacer(Modifier.height(8.dp))
        DRTextField(
            value = name,
            onValueChange = { onNameChanged(it.take(10).uppercase()) },
            leadingIcon = Icons.Rounded.Person,
            placeholder = "RACER",
            isError = name.isBlank(),
            testTag = "garage_name_input",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (name.isBlank()) "Name can't be empty" else "Shown to other players in LAN races. Max 10 characters.",
            style = DRType.Caption,
            color = if (name.isBlank()) DR.Bad else DR.TextLo
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = DR.TextDis, modifier = Modifier.size(16.dp))
            Text("Saved instantly", style = DRType.Caption, color = DR.TextLo)
        }
    }
}

/** Kept for the Shop: thumbnail with a chamfered frame. */
@Composable
fun KartThumbnailImage(
    body: KartBodyType,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(DRShape.Control)
            .background(DR.S3)
            .border(1.dp, accentColor.copy(alpha = 0.5f), DRShape.Control)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        KartThumb(body = body, modifier = Modifier.fillMaxSize())
    }
}
