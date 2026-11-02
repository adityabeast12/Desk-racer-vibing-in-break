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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ThreeSixty
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
private val LIVERY_NAMES = listOf("Stock", "Cyber", "Pro")
private val FINISH_NAMES = listOf("Gloss", "Matte", "Metal", "Pearl")

private fun KartBodyType.speedFrac() = ((topSpeedFactor - 0.8f) / 0.3f).coerceIn(0.05f, 1f)
private fun KartBodyType.accelFrac() = ((accelFactor - 0.8f) / 0.3f).coerceIn(0.05f, 1f)
private fun KartBodyType.handlingFrac() = ((handlingFactor - 0.8f) / 0.3f).coerceIn(0.05f, 1f)
private fun KartBodyType.weightFrac() = ((mass - 0.7f) / 0.65f).coerceIn(0.05f, 1f)
private fun fmt10(f: Float) = String.format(Locale.US, "%.1f", f * 10f)

fun bodyCoinPrice(body: KartBodyType): Int =
    ShopCatalog.cosmeticOffers.firstOrNull { it.unlocksBodyId == body.id }?.coinPrice ?: 600

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

        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)
        ) {
            DRScreenHeader(kicker = "Garage", title = "Customise", onBack = { handleBack() }, backTestTag = "btn_garage_back")

            // Left info column
            Column(Modifier.align(Alignment.TopStart).padding(top = 80.dp, start = 6.dp)) {
                Text(
                    text = draftBody.title.uppercase(),
                    style = DRType.Display.copy(fontSize = 31.sp, lineHeight = 31.sp),
                    color = DR.TextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(300.dp)
                )
                Text(
                    text = when (tab) {
                        1 -> "${KART_COLOR_NAMES.getOrElse(draftColor) { "Custom" }} ${FINISH_NAMES[draftFinish.coerceIn(0, 3)].lowercase()}"
                        2 -> "${LIVERY_NAMES.getOrElse(draftLivery) { "Stock" }} livery"
                        else -> draftBody.subtitle
                    },
                    style = DRType.Caption,
                    color = DR.TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(260.dp).padding(top = 4.dp, bottom = 12.dp)
                )
                val eq = profile.kartBody
                val cmp = draftBody != eq
                DRStatBar("Speed", draftBody.speedFrac(), fmt10(draftBody.speedFrac()), compare = if (cmp) eq.speedFrac() else null)
                DRStatBar("Accel", draftBody.accelFrac(), fmt10(draftBody.accelFrac()), compare = if (cmp) eq.accelFrac() else null)
                DRStatBar("Handling", draftBody.handlingFrac(), fmt10(draftBody.handlingFrac()), compare = if (cmp) eq.handlingFrac() else null)
                DRStatBar("Weight", draftBody.weightFrac(), fmt10(draftBody.weightFrac()), compare = if (cmp) eq.weightFrac() else null)
            }

            if (!hasDragged) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.ThreeSixty, contentDescription = null, tint = DR.TextLo, modifier = Modifier.size(18.dp))
                    Text("Drag to rotate", style = DRType.Caption, color = DR.TextLo)
                }
            }

            DRWallet(
                coins = profile.coinsBalance,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 356.dp, top = 6.dp)
            )

            // Right customisation panel
            DRPanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(344.dp)
                    .fillMaxHeight(),
                padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            ) {
                DRTabs(
                    items = listOf("Body", "Paint", "Livery", "Driver"),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.fillMaxWidth(),
                    testTagPrefix = "garage_tab"
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                    when (tab) {
                        0 -> BodyGrid(profile, draftBody) { preview(body = it) }
                        1 -> PaintTab(draftColor, draftFinish, onColor = { preview(c = it) }, onFinish = { preview(f = it) })
                        2 -> LiveryTab(draftBody, draftLivery) { preview(l = it) }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text(body.title, style = DRType.BodyStrong.copy(fontSize = 11.5.sp, lineHeight = 14.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun LiveryTab(body: KartBodyType, liveryIdx: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LIVERY_NAMES.forEachIndexed { i, name ->
            val sel = i == liveryIdx
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(DRShape.Card)
                    .background(if (sel) DR.AmberTint else DR.S2)
                    .border(if (sel) 1.5.dp else 1.dp, if (sel) DR.Amber else DR.Line, DRShape.Card)
                    .clickable(role = Role.RadioButton) { onPick(i) }
                    .padding(horizontal = 12.dp)
                    .testTag("garage_livery_$i"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KartThumb(body = body, modifier = Modifier.width(70.dp).height(46.dp))
                Column(Modifier.weight(1f)) {
                    Text(String.format(Locale.US, "%02d", i + 1), style = DRType.NumS.copy(fontSize = 10.sp), color = if (sel) DR.Amber else DR.TextDis)
                    Text("$name livery", style = DRType.Title.copy(fontSize = 17.sp), color = DR.TextHi)
                }
                if (sel) DRBadge("On", DRBadgeKind.AMBER)
            }
        }
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
