package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.HotPink
import com.example.ui.theme.InkBorder
import com.example.ui.theme.InkElevated
import com.example.ui.theme.InkSurface
import com.example.ui.theme.LampAmber
import com.example.ui.theme.NightNavy
import com.example.ui.theme.Poppins
import com.example.ui.theme.PrimaryCtaGradient
import com.example.ui.theme.SuccessLime
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.concurrent.ConcurrentHashMap

/**
 * Test hook registry for Part E6 automated Compose UI tests to verify `hasVisualOverflow == false`.
 */
object TextOverflowTracker {
    val results = ConcurrentHashMap<String, Boolean>()

    fun record(tag: String, result: TextLayoutResult) {
        results[tag] = result.hasVisualOverflow
    }

    fun clear() {
        results.clear()
    }
}

enum class DRButtonVariant {
    PRIMARY,
    SECONDARY,
    GHOST,
    DANGER
}

enum class DRButtonSize(val heightDp: Dp, val horizontalPad: Dp, val fontSize: TextUnit) {
    L(56.dp, 22.dp, 16.sp),
    M(48.dp, 18.dp, 14.sp),
    S(40.dp, 14.dp, 13.sp)
}

/**
 * Part E8: Unified DRButton (Primary, Secondary, Ghost, Danger; sizes L 56, M 48, S 40 dp;
 * 16 dp radius; pressed scale 0.96 + haptic tick; minimum touch target >= 48 dp).
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
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "dr_btn_scale"
    )

    val shape = RoundedCornerShape(16.dp)
    val contentColor = when {
        !enabled -> TextSecondary.copy(alpha = 0.45f)
        variant == DRButtonVariant.PRIMARY -> NightNavy
        variant == DRButtonVariant.DANGER -> NightNavy
        else -> TextPrimary
    }
    val borderStroke = when (variant) {
        DRButtonVariant.SECONDARY -> BorderStroke(1.5.dp, InkBorder)
        DRButtonVariant.GHOST -> BorderStroke(1.dp, InkBorder.copy(alpha = 0.55f))
        else -> null
    }

    val bgModifier = when {
        !enabled -> Modifier.background(InkElevated.copy(alpha = 0.5f), shape)
        variant == DRButtonVariant.PRIMARY -> Modifier.background(PrimaryCtaGradient, shape)
        variant == DRButtonVariant.SECONDARY -> Modifier.background(InkElevated, shape)
        variant == DRButtonVariant.DANGER -> Modifier.background(DangerCoral, shape)
        else -> Modifier.background(InkSurface.copy(alpha = 0.55f), shape)
    }

    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Box(
        modifier = taggedModifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = 48.dp, minHeight = size.heightDp.coerceAtLeast(48.dp))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(bgModifier)
            .then(if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            )
            .padding(horizontal = size.horizontalPad, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            AutoSizeText(
                text = text,
                style = DRType.Subtitle.copy(
                    fontSize = size.fontSize,
                    fontWeight = FontWeight.W700
                ),
                color = contentColor,
                maxLines = 1,
                minFontSize = 10.sp,
                tagForOverflowHook = testTag
            )
        }
    }
}

/**
 * Part E8: Unified DRCard container.
 */
@Composable
fun DRCard(
    modifier: Modifier = Modifier,
    containerColor: Color = InkSurface.copy(alpha = 0.92f),
    borderColor: Color = InkBorder,
    borderWidth: Dp = 1.dp,
    padding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val tagged = if (testTag != null) modifier.testTag(testTag) else modifier
    val clickMod = if (onClick != null) {
        val view = LocalView.current
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
    } else {
        Modifier
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = tagged.then(clickMod)
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

/**
 * Part E8: Unified DRChip pill/filter chip with >= 48.dp touch target when interactive.
 */
@Composable
fun DRChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accentColor: Color = LampAmber,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(12.dp)
    val bg = if (selected) accentColor.copy(alpha = 0.20f) else InkElevated.copy(alpha = 0.85f)
    val border = if (selected) accentColor else InkBorder
    val fg = if (selected) accentColor else TextPrimary
    val tagged = if (testTag != null) modifier.testTag(testTag) else modifier

    val clickMod = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Tab) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
    } else {
        Modifier
    }

    Box(
        modifier = tagged
            .clip(shape)
            .background(bg, shape)
            .border(1.5.dp, border, shape)
            .then(clickMod)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = fg,
                    modifier = Modifier.size(16.dp)
                )
            }
            AutoSizeText(
                text = text,
                style = DRType.Label.copy(fontWeight = FontWeight.W700, letterSpacing = 0.2.sp),
                color = fg,
                maxLines = 1,
                minFontSize = 10.sp,
                tagForOverflowHook = testTag
            )
        }
    }
}

/**
 * Part E8: Unified DRToggle row with label, optional subtitle, and M3 Switch.
 */
@Composable
fun DRToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    testTag: String? = null
) {
    val view = LocalView.current
    val tagged = if (testTag != null) modifier.testTag(testTag) else modifier
    Row(
        modifier = tagged
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(InkElevated.copy(alpha = 0.65f))
            .clickable(role = Role.Switch) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            AutoSizeText(
                text = label,
                style = DRType.Body.copy(fontWeight = FontWeight.W600),
                color = TextPrimary,
                maxLines = 1
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = DRType.Caption,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NightNavy,
                checkedTrackColor = LampAmber,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = InkSurface
            )
        )
    }
}

/**
 * Part E8: Unified DRStepper ([-] value [+] with >=48.dp buttons and >=8.dp spacing).
 */
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
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InkElevated.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoSizeText(
            text = label,
            style = DRType.Body.copy(fontWeight = FontWeight.W600),
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canDecrement) InkSurface else InkSurface.copy(alpha = 0.4f))
                    .border(1.dp, InkBorder, RoundedCornerShape(12.dp))
                    .clickable(enabled = canDecrement, role = Role.Button) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDecrement()
                    }
                    .testTag("${testTagPrefix}_dec"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease $label",
                    tint = if (canDecrement) LampAmber else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = valueText,
                style = DRType.TabularSubtitle,
                color = LampAmber,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(min = 44.dp, max = 96.dp)
                    .testTag("${testTagPrefix}_val")
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canIncrement) InkSurface else InkSurface.copy(alpha = 0.4f))
                    .border(1.dp, InkBorder, RoundedCornerShape(12.dp))
                    .clickable(enabled = canIncrement, role = Role.Button) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onIncrement()
                    }
                    .testTag("${testTagPrefix}_inc"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase $label",
                    tint = if (canIncrement) LampAmber else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Part E8: Unified DRTopBar (back left, title centre, optional action right).
 */
@Composable
fun DRTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backTestTag: String = "topbar_back_button",
    titleTestTag: String = "topbar_title",
    rightContent: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DRButton(
            text = "BACK",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            variant = DRButtonVariant.SECONDARY,
            size = DRButtonSize.M,
            onClick = onBack,
            testTag = backTestTag
        )
        AutoSizeText(
            text = title,
            style = DRType.Headline,
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .testTag(titleTestTag),
            textAlign = TextAlign.Center
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rightContent != null) {
                rightContent()
            } else {
                Spacer(modifier = Modifier.width(88.dp))
            }
        }
    }
}

/**
 * Part E4: AutoSizeText helper that steps font size down 1 sp at a time to a floor
 * so text never overflows or clips on small screens or 1.3x font scale.
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
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
        style = style.copy(
            fontFamily = Poppins,
            fontSize = currentSp.sp,
            lineHeight = (currentSp * 1.18f).sp
        ),
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
