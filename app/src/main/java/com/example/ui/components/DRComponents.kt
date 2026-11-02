package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.KartBodyType
import com.example.render.AssetRepository
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType
import com.example.ui.theme.SkewShape
import java.util.concurrent.ConcurrentHashMap

/* =====================================================================================
 * Desk GP component library (v6). Screens must not draw their own buttons/tabs/cards.
 * ===================================================================================== */

object TextOverflowTracker {
    val results = ConcurrentHashMap<String, Boolean>()
    fun record(tag: String, result: TextLayoutResult) {
        results[tag] = result.hasVisualOverflow
    }
    fun clear() = results.clear()
}

enum class DRButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }

enum class DRButtonSize(val heightDp: Dp, val horizontalPad: Dp, val fontSize: TextUnit) {
    L(50.dp, 26.dp, 17.sp),
    M(46.dp, 20.dp, 16.sp),
    S(40.dp, 14.dp, 14.sp)
}

private fun Modifier.tagged(tag: String?): Modifier = if (tag != null) this.testTag(tag) else this

/** 1 dp border that follows any (chamfer/skew) shape. */
fun Modifier.drBorder(width: Dp, color: Color, shape: Shape): Modifier = this.border(width, color, shape)

@Composable
private fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } }
}

/** Tracked uppercase label (Barlow SC). */
@Composable
fun DRLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DR.TextMid,
    style: TextStyle = DRType.LabelS,
    maxLines: Int = 1
) {
    Text(
        text = text.uppercase(),
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Unified button. PRIMARY = solid amber parallelogram with hard shadow (one per screen).
 * SECONDARY = chamfer outline. GHOST = text + icon. DANGER = red parallelogram (dialogs only).
 */
@Composable
fun DRButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DRButtonVariant = DRButtonVariant.PRIMARY,
    size: DRButtonSize = DRButtonSize.M,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val tick = rememberTick()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, spring(0.7f, 700f), label = "btn_scale")
    val skewed = variant == DRButtonVariant.PRIMARY || variant == DRButtonVariant.DANGER
    val shape: Shape = if (skewed) DRShape.Cta else DRShape.Card

    val fill = when {
        variant == DRButtonVariant.GHOST -> Color.Transparent
        !enabled -> DR.S4
        variant == DRButtonVariant.PRIMARY -> if (pressed) DR.AmberDk else DR.Amber
        variant == DRButtonVariant.DANGER -> DR.Bad
        else -> if (pressed) DR.S3 else DR.S2
    }
    val content = when {
        !enabled -> DR.TextDis
        variant == DRButtonVariant.PRIMARY -> DR.OnAmber
        variant == DRButtonVariant.DANGER -> Color.White
        variant == DRButtonVariant.GHOST -> DR.TextMid
        else -> DR.TextHi
    }
    val textStyle = if (skewed) {
        DRType.Title.copy(fontSize = size.fontSize, letterSpacing = 0.3.sp)
    } else {
        DRType.Label.copy(fontSize = (size.fontSize.value * 0.78f).sp)
    }

    Box(
        modifier = modifier
            .tagged(testTag)
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = if (variant == DRButtonVariant.GHOST) 48.dp else 96.dp, minHeight = size.heightDp)
            .height(size.heightDp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (pressed && enabled && skewed) 2.dp.toPx() else 0f
            }
            .drawBehind {
                if (skewed && enabled && !pressed) {
                    val outline = shape.createOutline(this.size, layoutDirection, this)
                    translate(top = 3.dp.toPx()) { drawOutline(outline, Color.Black.copy(alpha = 0.35f)) }
                }
            }
            .clip(shape)
            .background(fill)
            .then(if (variant == DRButtonVariant.SECONDARY) Modifier.border(1.dp, DR.Line2, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    tick()
                    onClick()
                }
            )
            .padding(horizontal = if (variant == DRButtonVariant.GHOST) 8.dp else size.horizontalPad),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (skewed) 20.dp else 18.dp))
                Spacer(Modifier.width(7.dp))
            }
            AutoSizeText(
                text = text.uppercase(),
                style = textStyle,
                color = content,
                maxLines = 1,
                minFontSize = 10.sp,
                tagForOverflowHook = testTag
            )
        }
    }
}

/** 48 dp chamfered back button. Always top-left on full-screen routes. */
@Composable
fun DRBackButton(onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String = "nav_back") {
    val tick = rememberTick()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag(testTag)
            .size(48.dp)
            .clip(DRShape.Card)
            .background(DR.S2)
            .border(1.dp, DR.Line2, DRShape.Card)
            .clickable(role = Role.Button) {
                tick()
                onClick()
            }
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = DR.TextHi, modifier = Modifier.size(22.dp))
    }
}

/** Close button for dialogs/sheets only: 40 dp visual inside a 48 dp hit area. */
@Composable
fun DRCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String = "nav_close") {
    val tick = rememberTick()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag(testTag)
            .size(48.dp)
            .clickable(role = Role.Button) {
                tick()
                onClick()
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(DRShape.Control)
                .background(DR.S3)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = DR.TextMid, modifier = Modifier.size(20.dp))
        }
    }
}

/** Back button + kicker + ALL CAPS Kanit title, used at the top-left of every full screen. */
@Composable
fun DRScreenHeader(
    kicker: String,
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backTestTag: String = "nav_back"
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) DRBackButton(onClick = onBack, testTag = backTestTag)
        Column {
            DRLabel(kicker, color = DR.Amber)
            Spacer(Modifier.height(2.dp))
            Text(
                text = title.uppercase(),
                style = DRType.Title,
                color = DR.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DRCoin(size: Dp = 16.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFFE38A), Color(0xFFF2A900), Color(0xFFB87800))))
            .border(1.5.dp, DR.Gold, CircleShape)
    )
}

@Composable
fun DRGem(size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.05f)
            lineTo(w * 0.95f, h * 0.4f)
            lineTo(w * 0.5f, h * 0.95f)
            lineTo(w * 0.05f, h * 0.4f)
            close()
        }
        drawPath(p, DR.Boost)
    }
}

/** Coins (+ optional gems) chip, top-right of hub screens. */
@Composable
fun DRWallet(coins: Int, gems: Int? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(DRShape.Card)
            .background(DR.S1)
            .border(1.dp, DR.Line, DRShape.Card)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DRCoin()
            Text(formatThousands(coins), style = DRType.NumM.copy(fontSize = 16.sp), color = DR.TextHi)
        }
        if (gems != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DRGem()
                Text(formatThousands(gems), style = DRType.NumM.copy(fontSize = 16.sp), color = DR.TextHi)
            }
        }
    }
}

fun formatThousands(v: Int): String = String.format(java.util.Locale.US, "%,d", v)

/** Indexed tabs: "01 BODY" with a 2 dp amber underline on the selected tab. */
@Composable
fun DRTabs(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "tab"
) {
    val tick = rememberTick()
    Row(
        modifier = modifier
            .height(40.dp)
            .drawBehind {
                drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            },
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items.forEachIndexed { i, t ->
            val sel = i == selected
            val underline by animateDpAsState(if (sel) 2.dp else 0.dp, tween(160), label = "tab_ul")
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .testTag("${testTagPrefix}_$i")
                    .clickable(role = Role.Tab) {
                        tick()
                        onSelect(i)
                    }
                    .drawBehind {
                        val h = underline.toPx()
                        if (h > 0f) drawRect(DR.Amber, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                    }
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(String.format(java.util.Locale.US, "%02d", i + 1), style = DRType.NumS.copy(fontSize = 10.sp), color = if (sel) DR.Amber else DR.TextDis)
                Text(t.uppercase(), style = DRType.Label.copy(fontSize = 12.5.sp), color = if (sel) DR.TextHi else DR.TextLo, maxLines = 1)
            }
        }
    }
}

/** Segmented control: one amber fill on the selected option. */
@Composable
fun DRSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    testTagPrefix: String = "seg"
) {
    val tick = rememberTick()
    Row(
        modifier = modifier
            .height(height)
            .clip(DRShape.Control)
            .background(DR.S1)
            .border(1.dp, DR.Line, DRShape.Control)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) DR.Amber else Color.Transparent, tween(160), label = "seg_bg")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("${testTagPrefix}_$i")
                    .background(bg)
                    .clickable(role = Role.RadioButton) {
                        tick()
                        onSelect(i)
                    }
            ) {
                AutoSizeText(
                    text = o.uppercase(),
                    style = DRType.Label.copy(fontSize = 11.5.sp),
                    color = if (sel) DR.OnAmber else DR.TextMid,
                    minFontSize = 8.sp
                )
            }
        }
    }
}

/** Skewed on/off switch (visual only; the row handles the click). */
@Composable
fun DRSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(if (checked) 23.dp else 5.dp, tween(160), label = "sw_knob")
    val track by animateColorAsState(if (checked) DR.Amber else DR.S4, tween(160), label = "sw_track")
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(SkewShape(12f))
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .offset(x = knob, y = 4.dp)
                .size(width = 16.dp, height = 18.dp)
                .clip(SkewShape(12f))
                .background(if (checked) DR.OnAmber else DR.TextLo)
        )
    }
}

/** Toggle row: title + subtitle on the left, DRSwitch on the right; whole row clickable. */
@Composable
fun DRToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    testTag: String? = null
) {
    val tick = rememberTick()
    Row(
        modifier = modifier
            .tagged(testTag)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(role = Role.Switch) {
                tick()
                onCheckedChange(!checked)
            }
            .drawBehind {
                drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = DRType.BodyStrong.copy(fontSize = 13.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = DRType.Caption.copy(fontSize = 11.sp), color = DR.TextMid, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        DRSwitch(checked)
    }
}

/** Label + tabular value with − / + controls. */
@Composable
fun DRStepper(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
    testTagPrefix: String = "stepper"
) {
    val tick = rememberTick()
    @Composable
    fun StepBtn(icon: ImageVector, enabled: Boolean, desc: String, tag: String, action: () -> Unit) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .testTag(tag)
                .size(48.dp)
                .clickable(enabled = enabled, role = Role.Button) {
                    tick()
                    action()
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(DRShape.Control)
                    .background(DR.S2)
                    .border(1.dp, DR.Line, DRShape.Control)
            ) {
                Icon(icon, contentDescription = desc, tint = if (enabled) DR.TextHi else DR.TextDis, modifier = Modifier.size(18.dp))
            }
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .drawBehind {
                drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = DRType.BodyStrong.copy(fontSize = 13.sp), color = DR.TextHi, maxLines = 1, modifier = Modifier.weight(1f))
        StepBtn(Icons.Rounded.Remove, canDecrement, "Decrease $label", "${testTagPrefix}_dec", onDecrement)
        Text(
            text = valueText,
            style = DRType.NumL,
            color = DR.TextHi,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 40.dp).testTag("${testTagPrefix}_val")
        )
        StepBtn(Icons.Rounded.Add, canIncrement, "Increase $label", "${testTagPrefix}_inc", onIncrement)
    }
}

/** Slider with label + tabular value; skewed thumb, amber active track. */
@Composable
fun DRSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    valueText: String = "${(value * 100).toInt()}",
    testTag: String? = null
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val frac = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var widthPx by remember { mutableStateOf(1f) }
    fun setFromX(x: Float) {
        val f = (x / widthPx).coerceIn(0f, 1f)
        onValueChange(valueRange.start + f * span)
    }
    Column(
        modifier = modifier
            .tagged(testTag)
            .fillMaxWidth()
            .drawBehind {
                drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(top = 6.dp, bottom = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = DRType.BodyStrong.copy(fontSize = 13.sp), color = DR.TextHi, maxLines = 1)
            Text(valueText, style = DRType.NumM, color = DR.TextHi)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInputSlider(
                    onWidth = { widthPx = it },
                    onX = { setFromX(it) }
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                widthPx = size.width
                val cy = size.height / 2f
                val th = 4.dp.toPx()
                drawRect(DR.S4, topLeft = Offset(0f, cy - th / 2), size = Size(size.width, th))
                drawRect(DR.Amber, topLeft = Offset(0f, cy - th / 2), size = Size(size.width * frac, th))
                val kw = 14.dp.toPx()
                val kh = 18.dp.toPx()
                val kx = (size.width * frac - kw / 2).coerceIn(0f, size.width - kw)
                val sk = kh * 0.21f
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(kx + sk, cy - kh / 2)
                    lineTo(kx + kw, cy - kh / 2)
                    lineTo(kx + kw - sk, cy + kh / 2)
                    lineTo(kx, cy + kh / 2)
                    close()
                }
                drawPath(p, DR.TextHi)
            }
        }
    }
}

private fun Modifier.pointerInputSlider(onWidth: (Float) -> Unit, onX: (Float) -> Unit): Modifier =
    this
        .pointerInput("tap") { detectTapGestures { onX(it.x) } }
        .pointerInput("drag") { detectHorizontalDragGestures { change, _ -> onX(change.position.x) } }

/** Filter chip (32 dp visual, 48 dp hit). */
@Composable
fun DRChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accentColor: Color = DR.Amber,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val tick = rememberTick()
    val fg = if (selected) DR.OnAmber else DR.TextMid
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .tagged(testTag)
            .then(
                if (onClick != null) Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Tab) {
                        tick()
                        onClick()
                    } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(DRShape.Control)
                .background(if (selected) accentColor else DR.S2)
                .then(if (!selected) Modifier.border(1.dp, DR.Line, DRShape.Control) else Modifier)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            AutoSizeText(text.uppercase(), style = DRType.Label.copy(fontSize = 11.sp), color = fg, minFontSize = 8.sp, tagForOverflowHook = testTag)
        }
    }
}

enum class DRBadgeKind { AMBER, YOU, OK, MUTED, BOOST, BAD }

@Composable
fun DRBadge(text: String, kind: DRBadgeKind = DRBadgeKind.AMBER, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        DRBadgeKind.AMBER -> DR.Amber to DR.OnAmber
        DRBadgeKind.YOU -> DR.You to Color.White
        DRBadgeKind.OK -> DR.Ok to DR.OnAmber
        DRBadgeKind.MUTED -> DR.S4 to DR.TextMid
        DRBadgeKind.BOOST -> DR.Boost to DR.OnAmber
        DRBadgeKind.BAD -> DR.Bad to Color.White
    }
    Box(
        modifier = modifier
            .clip(SkewShape(14f))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(cleanLabel(text), style = DRType.LabelS.copy(fontSize = 9.5.sp, letterSpacing = 1.2.sp), color = fg, maxLines = 1)
    }
}

/** Strips decorative glyphs (★ ✓ • emoji) that Barlow/Kanit don't contain, then uppercases. */
fun cleanLabel(text: String): String =
    text.filter { it.isLetterOrDigit() || it in " .:/·+-%'#" }.trim().uppercase()

/** Highlighter tag — max one per screen (YOUR BEST, NEW BEST, NEW). */
@Composable
fun DRMarkerTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .rotate(-2f)
            .background(DR.Marker)
            .padding(horizontal = 7.dp, vertical = 1.dp)
    ) {
        Text(text.uppercase(), style = DRType.LabelS, color = DR.Ink, maxLines = 1)
    }
}

/** 10-segment stat bar. [compare] (0..1) flashes gains green / losses red when previewing. */
@Composable
fun DRStatBar(name: String, fraction: Float, valueText: String, modifier: Modifier = Modifier, compare: Float? = null, barWidth: Dp = 112.dp) {
    val filled = (fraction.coerceIn(0f, 1f) * 10f).toInt().coerceIn(0, 10).let { if (fraction > 0f && it == 0) 1 else it }
    val base = compare?.let { (it.coerceIn(0f, 1f) * 10f).toInt().coerceIn(0, 10) }
    Row(modifier = modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        DRLabel(name, modifier = Modifier.width(72.dp))
        Row(Modifier.width(barWidth), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (k in 0 until 10) {
                val c = when {
                    base != null && k < filled && k >= base -> DR.Ok
                    base != null && k >= filled && k < base -> DR.Bad
                    k < filled -> DR.Amber
                    else -> DR.S4
                }
                Box(Modifier.weight(1f).height(6.dp).background(c))
            }
        }
        Text(valueText, style = DRType.NumM, color = DR.TextHi, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
    }
}

/** Skewed 6 dp progress bar with optional label row. */
@Composable
fun DRProgress(value: Float, modifier: Modifier = Modifier, label: String? = null, trailing: String? = null) {
    Column(modifier) {
        if (label != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                DRLabel(label)
                Text(trailing ?: "${(value * 100).toInt()}%", style = DRType.NumS, color = DR.TextMid)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(SkewShape(45f))
                .background(DR.S4)
        ) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().background(DR.Amber))
        }
    }
}

/** 4 dp red/white kerb stripe. */
@Composable
fun DRKerbStrip(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val stripe = 18.dp.toPx()
        var x = 0f
        var i = 0
        while (x < size.width) {
            drawRect(if (i % 2 == 0) DR.KerbWhite else DR.Kerb, topLeft = Offset(x, 0f), size = Size(stripe, size.height))
            x += stripe
            i++
        }
    }
}

@Composable
fun DRStatusDot(color: Color = DR.Ok, modifier: Modifier = Modifier) {
    Box(modifier.size(16.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)))
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

/** Chamfered panel container (S1 at 95%). */
@Composable
fun DRPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    color: Color = DR.S1.copy(alpha = 0.95f),
    borderColor: Color = DR.Line,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(DRShape.Panel)
            .background(color)
            .border(1.dp, borderColor, DRShape.Panel)
            .padding(padding),
        content = content
    )
}

/** Legacy card API → chamfered card. */
@Composable
fun DRCard(
    modifier: Modifier = Modifier,
    containerColor: Color = DR.S2,
    borderColor: Color = DR.Line,
    borderWidth: Dp = 1.dp,
    padding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tick = rememberTick()
    Column(
        modifier = modifier
            .tagged(testTag)
            .clip(DRShape.Card)
            .background(containerColor)
            .border(borderWidth, borderColor, DRShape.Card)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button) {
                    tick()
                    onClick()
                } else Modifier
            )
            .padding(padding),
        content = content
    )
}

/** Index · icon · title/subtitle · chevron menu row (48 dp). */
@Composable
fun DRMenuRow(
    index: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val tick = rememberTick()
    Row(
        modifier = modifier
            .tagged(testTag)
            .fillMaxWidth()
            .height(48.dp)
            .clickable(role = Role.Button) {
                tick()
                onClick()
            }
            .drawBehind {
                drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(String.format(java.util.Locale.US, "%02d", index), style = DRType.NumS.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W700), color = DR.TextDis, modifier = Modifier.width(18.dp))
        Icon(icon, contentDescription = null, tint = DR.TextMid, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = DRType.BodyStrong, color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = DRType.Caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp), color = DR.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = DR.TextDis, modifier = Modifier.size(20.dp))
    }
}

/** Single-line text field with chamfer, amber focus border, red error state. */
@Composable
fun DRTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    placeholder: String = "",
    isError: Boolean = false,
    textStyle: TextStyle = DRType.BodyStrong.copy(fontSize = 15.sp),
    testTag: String? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> DR.Bad
        focused -> DR.Amber
        else -> DR.Line2
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(DRShape.Card)
            .background(DR.S2)
            .border(if (isError || focused) 1.5.dp else 1.dp, borderColor, DRShape.Card)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = DR.TextLo, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = textStyle, color = DR.TextDis, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = DR.TextHi),
                cursorBrush = SolidColor(DR.Amber),
                keyboardOptions = keyboardOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .tagged(testTag)
                    .onFocusChangedCompat { focused = it }
            )
        }
    }
}

private fun Modifier.onFocusChangedCompat(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { onChange(it.isFocused) }

/** Toast: 48 dp, kind-coloured left edge + icon. */
@Composable
fun DRToast(text: String, icon: ImageVector, color: Color = DR.Ok, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(min = 220.dp, max = 420.dp)
            .height(48.dp)
            .clip(DRShape.Card)
            .background(DR.S2)
            .drawBehind { drawRect(color, size = Size(3.dp.toPx(), size.height)) }
            .padding(horizontal = 14.dp)
            .testTag("hud_toast"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = DRType.BodyStrong.copy(fontSize = 12.5.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Confirmation dialog over a scrim. Close is top-right; the safe action sits LEFT of the risky one.
 */
@Composable
fun DRDialog(
    kicker: String,
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    confirmIcon: ImageVector? = null,
    testTag: String = "dr_dialog"
) {
    val accent = if (destructive) DR.Bad else DR.Amber
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(DR.Scrim)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss)
            .testTag(testTag)
    ) {
        Box(
            modifier = modifier
                .widthIn(max = 420.dp)
                .padding(16.dp)
                .clip(DRShape.Panel)
                .background(DR.S1)
                .border(1.dp, DR.Line2, DRShape.Panel)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .drawBehind { drawRect(accent, size = Size(4.dp.toPx(), size.height)) }
        ) {
            Column(Modifier.padding(start = 26.dp, end = 22.dp, top = 22.dp, bottom = 20.dp)) {
                DRLabel(kicker, color = accent, modifier = Modifier.padding(end = 40.dp))
                Spacer(Modifier.height(6.dp))
                Text(title, style = DRType.Title.copy(fontSize = 25.sp, lineHeight = 27.sp), color = DR.TextHi, modifier = Modifier.padding(end = 36.dp))
                Spacer(Modifier.height(8.dp))
                Text(body, style = DRType.Body, color = DR.TextMid, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    DRButton(dismissText, onClick = onDismiss, variant = DRButtonVariant.SECONDARY, size = DRButtonSize.M, testTag = "${testTag}_dismiss")
                    DRButton(
                        confirmText,
                        onClick = onConfirm,
                        variant = if (destructive) DRButtonVariant.DANGER else DRButtonVariant.PRIMARY,
                        size = DRButtonSize.M,
                        icon = confirmIcon,
                        testTag = "${testTag}_confirm"
                    )
                }
            }
            DRCloseButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
        }
    }
}

/** Full-screen scaffold: v6 background, safe insets, gutters. */
@Composable
fun DRScreen(
    modifier: Modifier = Modifier,
    background: Color = DR.Bg.copy(alpha = 0.86f),
    testTag: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .tagged(testTag)
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}


/** Kart thumbnail from textures/ui/karts/<slug>.png. Locked → greyscale + dim. */
@Composable
fun KartThumb(body: KartBodyType, modifier: Modifier = Modifier, locked: Boolean = false) {
    val context = LocalContext.current
    val bitmap = remember(body) {
        // drmAssetPath is e.g. models/kart_truck_red.drm or models/props/toy_car.drm → kart_truck_red.png / toy_car.png
        val name = body.drmAssetPath.substringAfterLast('/').substringBeforeLast('.')
        (AssetRepository.loadUiBitmapStatic(context, "textures/ui/karts/$name.png")
            ?: AssetRepository.loadUiBitmapStatic(context, "textures/ui/karts/kart_$name.png"))?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = body.title,
            contentScale = ContentScale.Fit,
            colorFilter = if (locked) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
            alpha = if (locked) 0.45f else 1f,
            modifier = modifier
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.7f).height(12.dp).clip(DRShape.Control).background(if (locked) DR.S4 else DR.Line2))
        }
    }
}

/** Legacy top bar → back button + header + right slot. */
@Composable
fun DRTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backTestTag: String = "topbar_back_button",
    titleTestTag: String = "topbar_title",
    kicker: String = "DESK RACER",
    rightContent: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().height(52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DRScreenHeader(kicker = kicker, title = title, onBack = onBack, backTestTag = backTestTag, modifier = Modifier.weight(1f).testTag(titleTestTag))
        if (rightContent != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = rightContent)
        }
    }
}

/** Steps font size down to a floor so text never overflows. Keeps the style's font family. */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = DR.TextHi,
    maxLines: Int = 1,
    minFontSize: TextUnit = 10.sp,
    textAlign: TextAlign? = null,
    tagForOverflowHook: String? = null
) {
    val initialSp = if (style.fontSize.value > 0f) style.fontSize.value else 14f
    val floorSp = minFontSize.value.coerceAtLeast(8f)
    var currentSp by remember(text, initialSp) { mutableStateOf(initialSp) }
    Text(
        text = text,
        style = style.copy(fontSize = currentSp.sp, lineHeight = (currentSp * 1.18f).sp),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && currentSp > floorSp) {
                currentSp = (currentSp - 1f).coerceAtLeast(floorSp)
            } else if (tagForOverflowHook != null) {
                TextOverflowTracker.record(tagForOverflowHook, result)
            }
        }
    )
}
