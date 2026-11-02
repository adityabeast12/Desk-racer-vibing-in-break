# DESK RACER LAN — v6 "DESK GP" DESIGN SYSTEM OVERHAUL
### Paste this whole document into Google AI Studio. It replaces every visual decision from the v5 UI prompt. Gameplay, physics, networking and ViewModels stay exactly as they are; this is a **presentation-layer rewrite** of every screen and every UI component.

---

## PART 0 — READ FIRST: SCOPE, GUARDRAILS, ASSETS

**What you are doing.** You are re-skinning the whole app into one coherent motorsport identity called **Desk GP**. It should feel like a premium racing game (think the restraint of Gran Turismo's menus and the punch of a Mario Kart HUD) while staying playful, because the cars are toys racing across desks.

**Do not touch.**
- `signingConfigs`, `applicationId`, `namespace` and the `plugins {}` block in any Gradle file.
- Networking (`net/`), the host-authoritative snapshot/event code, physics, the track loader, the asset loader and all ViewModel logic and state classes.
- Existing unit tests (`MultiplayerFakeNetworkTest`, etc.). Keep them passing, and add the new UI tests listed in Part 9.

**Visual reference.** The `design/v6/` folder of the package holds 16 PNG mockups plus their HTML sources. They are the source of truth for layout. When this text and a mockup disagree, **the text wins for numbers (dp, sp, colours) and the mockup wins for composition.**

| File | Screen |
|---|---|
| `00_component_kit.png` | Every component and every state |
| `01_main_menu.png` | Main menu |
| `02_mode_select.png` | Mode select |
| `03_track_select.png` | Track select / race setup |
| `04_garage_body.png`, `05_garage_paint.png` | Garage (Body tab, Paint tab) |
| `06_lobby_host.png`, `07_lobby_join.png` | LAN host lobby, LAN join |
| `08_hud_race.png`, `09_hud_countdown.png` | Race HUD (racing and countdown states) |
| `10_pause.png` | Pause |
| `11_results.png` | Results |
| `12_settings.png` | Settings (Controls page) |
| `13_shop.png` | Shop / Desk Pass |
| `14_loading.png` | Loading / player sync |
| `15_dialog_toast.png` | Confirmation dialog + toast |

**Fonts (all SIL OFL 1.1, bundled, offline).** These go in `app/src/main/res/font/`. Poppins is already there; add the 7 new files from the package's `font/` folder.

```
kanit_bold_italic.ttf        kanit_extrabold_italic.ttf   kanit_black_italic.ttf
barlow_sc_medium.ttf         barlow_sc_semibold.ttf       barlow_sc_bold.ttf     barlow_sc_bold_italic.ttf
(existing) poppins_regular / medium / semibold / bold / extrabold / black / italic
```
Do **not** use downloadable Google Fonts. The game must work with no internet on a hotspot.

---

## PART 1 — WHY v5 STILL LOOKED GENERIC (fix all of these)

1. **Rounded glass everywhere.** Every app on the Play Store has translucent rounded cards. They carry no identity. → Replace with **chamfered** (cut-corner) solid panels.
2. **One font for everything.** Poppins titles, labels and numbers all looked the same, so nothing had hierarchy. → Use **three families with three jobs** (Part 3).
3. **Numbers jittered.** Poppins has **no tabular figures** (`tnum` does nothing on it; its "1" is narrower than its "8"), so timers and speed wobbled every frame. → Every live number uses **Barlow Semi Condensed** with `fontFeatureSettings = "tnum"`.
4. **Too many accent colours** (amber, cyan and pink were all used for "selected"). → **Amber is the only interactive colour.** Pink means only "you". Cyan means only boost.
5. **Pill buttons with gradients** read as "template". → Primary CTA = **solid amber parallelogram** with a 12° skew and a hard drop shadow. One per screen.
6. **Labels without system.** → Every tab and section is **indexed** (`01 BODY`, `02 PAINT`) in tracked caps. This is the single most "premium racing game" detail.
7. **Back/close buttons floated and were inconsistent.** → One `DRBackButton` (48dp, top-left, every full screen) and one `DRCloseButton` (dialogs only, top-right inside the panel).
8. **No brand texture.** → A 4dp **kerb strip** (red/white) on the bottom edge of hub screens and the top edge of Results, a yellow **marker tag** for one personal-best/new item per screen, and Kanit Black Italic for hero names.

---

## PART 2 — DESIGN PRINCIPLES (use these to decide anything this doc doesn't cover)

1. **One job per colour.** Amber = you can press it / it is selected. Pink = you. Cyan = boost. Green = ready/connected. Red = danger/disconnect. Gold/silver/bronze = podium only.
2. **One primary action per screen**, bottom-right, always the skewed amber button. Everything else is secondary (outlined chamfer) or ghost (text + icon).
3. **The kart is the hero.** In Garage, Main Menu and Shop, the 3D/rendered kart is the largest element. Panels sit at the edges and never cover it.
4. **Show data before commitment.** Stats, price, laps, track length and player count are visible *before* the player presses the CTA.
5. **Bottom-aligned, thumb-first.** Landscape phones are held by both thumbs. Primary actions live in the bottom 30% of the screen; navigation (back) is top-left; status (wallet) is top-right.
6. **HUD: 5 persistent elements maximum**: position+lap+time, coins, minimap, item, controls. The centre third of the screen stays clear.
7. **Chamfer, don't round.** Rounded corners are allowed **only** for coins, status dots and the minimap kart dots.
8. **Motion confirms, never decorates.** Every animation answers "what changed?" Nothing loops in menus except the garage turntable.

---

## PART 3 — TOKENS (create these files exactly; nothing else may hardcode values)

### 3.1 `ui/theme/Color.kt`
```kotlin
package com.example.ui.theme
import androidx.compose.ui.graphics.Color

object DR {
    // Surfaces (darkest → lightest)
    val Bg        = Color(0xFF0C0D10)  // app background
    val S1        = Color(0xFF121419)  // panels, dialogs
    val S2        = Color(0xFF17191F)  // cards, controls
    val S3        = Color(0xFF1E2128)  // raised control (close btn)
    val S4        = Color(0xFF272A33)  // tracks of sliders/progress, disabled fill
    val Line      = Color(0xFF262932)  // 1dp dividers & card borders
    val Line2     = Color(0xFF343843)  // stronger border (back btn, secondary btn)
    // Text
    val TextHi    = Color(0xFFF2F2F0)
    val TextMid   = Color(0xFFA2A7B2)
    val TextLo    = Color(0xFF6E7380)
    val TextDis   = Color(0xFF4A4E59)
    // Interactive (the ONLY selected/primary colour)
    val Amber     = Color(0xFFFFB000)
    val AmberHi   = Color(0xFFFFC233)  // focus ring / hover
    val AmberDk   = Color(0xFFC98A00)  // pressed
    val OnAmber   = Color(0xFF0C0D10)
    val AmberTint = Color(0xFF2A2410)  // selected card fill
    // Semantics
    val You       = Color(0xFFFF3D7F)  // the local player, nothing else
    val Boost     = Color(0xFF2DE2E6)  // drift/boost meter, nothing else
    val Ok        = Color(0xFF3DDC84)
    val Bad       = Color(0xFFFF4D4F)
    val Kerb      = Color(0xFFD7262B)
    val KerbWhite = Color(0xFFE8E8E6)
    val Gold      = Color(0xFFFFD23F); val Silver = Color(0xFFC9D1DB); val Bronze = Color(0xFFE0915A)
    val Marker    = Color(0xFFFFD23F); val Ink = Color(0xFF14110C)   // highlighter tag
    // Overlays
    val Scrim     = Color(0xB8060709)  // 72% – dialogs
    val HudPlate  = Color(0x610C0D10)  // 38% – HUD control backing
    val HudPlate2 = Color(0x8C0C0D10)  // 55% – HUD text backing
    val YouWash   = Color(0x1FFF3D7F)  // 12% – "you" row background
}
```

### 3.2 `ui/theme/Type.kt`
```kotlin
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.W400), Font(R.font.poppins_medium, FontWeight.W500),
    Font(R.font.poppins_semibold, FontWeight.W600), Font(R.font.poppins_bold, FontWeight.W700))
val Kanit = FontFamily(
    Font(R.font.kanit_bold_italic, FontWeight.W700, FontStyle.Italic),
    Font(R.font.kanit_extrabold_italic, FontWeight.W800, FontStyle.Italic),
    Font(R.font.kanit_black_italic, FontWeight.W900, FontStyle.Italic))
val BarlowSC = FontFamily(
    Font(R.font.barlow_sc_medium, FontWeight.W500), Font(R.font.barlow_sc_semibold, FontWeight.W600),
    Font(R.font.barlow_sc_bold, FontWeight.W700), Font(R.font.barlow_sc_bold_italic, FontWeight.W700, FontStyle.Italic))

private val noPad = PlatformTextStyle(includeFontPadding = false)
private val trim  = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

object DRType {
    // DISPLAY — Kanit Black Italic. Hero names, race position, PAUSED, results headline. ALL CAPS.
    val DisplayXL = TextStyle(fontFamily = Kanit, fontWeight = FontWeight.W900, fontStyle = FontStyle.Italic, fontSize = 72.sp, lineHeight = 66.sp, letterSpacing = (-0.5).sp, platformStyle = noPad, lineHeightStyle = trim)
    val Display   = DisplayXL.copy(fontSize = 40.sp, lineHeight = 38.sp)
    val DisplayM  = DisplayXL.copy(fontSize = 32.sp, lineHeight = 31.sp)
    // TITLE — Kanit ExtraBold Italic. Screen titles (ALL CAPS), card titles (Title Case).
    val Title     = TextStyle(fontFamily = Kanit, fontWeight = FontWeight.W800, fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 24.sp, platformStyle = noPad, lineHeightStyle = trim)
    val TitleS    = Title.copy(fontSize = 18.sp, lineHeight = 20.sp)
    // LABEL — Barlow SC Bold, ALWAYS uppercase, tracked +0.14em. Tabs, kickers, button text, badges.
    val Label     = TextStyle(fontFamily = BarlowSC, fontWeight = FontWeight.W700, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 1.7.sp, platformStyle = noPad)
    val LabelS    = Label.copy(fontSize = 10.5.sp, letterSpacing = 1.5.sp)
    // NUMERIC — Barlow SC with tabular figures. EVERY number that can change.
    private val num = TextStyle(fontFamily = BarlowSC, fontWeight = FontWeight.W700, fontFeatureSettings = "tnum", platformStyle = noPad)
    val NumXL = num.copy(fontSize = 28.sp, lineHeight = 30.sp, fontStyle = FontStyle.Italic) // HUD speed
    val NumL  = num.copy(fontSize = 20.sp, lineHeight = 22.sp)   // HUD timer, lap, results totals
    val NumM  = num.copy(fontSize = 15.sp, lineHeight = 18.sp)   // stat values, prices, times in lists
    val NumS  = num.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.W600) // gaps, ping
    // BODY — Poppins. Names, descriptions, settings rows.
    val BodyStrong = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.W600, fontSize = 13.5.sp, lineHeight = 18.sp, platformStyle = noPad)
    val Body       = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 19.sp, platformStyle = noPad)
    val Caption    = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.W400, fontSize = 11.5.sp, lineHeight = 16.sp, color = DR.TextMid, platformStyle = noPad)
}
```
**Rules.**
- `Label` text is passed through `.uppercase()` inside `DRLabel`. Never type caps by hand.
- HUD text uses `DRType` sizes, multiplied by `min(LocalDensity.current.fontScale, 1.3f) / fontScale`. HUD text caps at 1.3× system font scale so it can never cover the track. Menus scale freely.
- Never use `FontFamily.Default`, `MaterialTheme.typography` or a raw `.sp` value outside `Type.kt`.

### 3.3 `ui/theme/Shapes.kt`
```kotlin
/** Cut top-right and bottom-left corners. The signature shape of Desk GP. */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        return Outline.Generic(Path().apply {
            moveTo(0f, 0f); lineTo(size.width - c, 0f); lineTo(size.width, c)
            lineTo(size.width, size.height); lineTo(c, size.height); lineTo(0f, size.height - c); close()
        })
    }
}
/** Parallelogram leaning right, for primary CTA, HUD gas/item, toggles, progress, badges. */
class SkewShape(private val angleDeg: Float = 12f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val dx = size.height * tan(Math.toRadians(angleDeg.toDouble())).toFloat()
        return Outline.Generic(Path().apply {
            moveTo(dx, 0f); lineTo(size.width, 0f); lineTo(size.width - dx, size.height); lineTo(0f, size.height); close()
        })
    }
}
object DRShape {
    val Panel   = ChamferShape(18.dp)   // big panels, dialogs, mode cards, track hero
    val Card    = ChamferShape(8.dp)    // item cards, buttons (secondary), back button, toasts
    val Control = ChamferShape(6.dp)    // steppers, segmented, chips, swatches, code boxes, close btn
    val HudPad  = ChamferShape(10.dp)   // HUD steer pads, minimap
    val Cta     = SkewShape(12f)
    val HudSkew = SkewShape(10f)
}
```
Borders use `Modifier.border(1.dp, DR.Line, DRShape.Card)`, which follows the chamfer exactly. The selected state is `1.5.dp, DR.Amber`.

### 3.4 `ui/theme/Dimens.kt`
```kotlin
object DRDim {
    val Gutter = 16.dp; val GutterL = 20.dp          // screen edges (add WindowInsets.safeDrawing on top)
    val S1 = 4.dp; val S2 = 8.dp; val S3 = 12.dp; val S4 = 16.dp; val S5 = 20.dp; val S6 = 24.dp
    val Touch = 48.dp                                // minimum hit target, no exceptions
    val CtaH = 50.dp; val CtaHs = 46.dp; val BtnH = 48.dp; val RowH = 48.dp; val ToggleRowH = 52.dp
    val SidePanelW = 340.dp                          // garage/track-setup/results right panel
    val TabH = 40.dp; val KerbH = 4.dp; val Divider = 1.dp
}
```

### 3.5 `ui/theme/Motion.kt`
```kotlin
object DRMotion {
    const val Press = 90; const val Select = 160; const val Panel = 220; const val Screen = 320
    val Ease = FastOutSlowInEasing
    val Snappy = spring<Float>(dampingRatio = 0.72f, stiffness = 700f)
}
```
- **Press:** scale 0.97, offsetY +2dp, colour → `AmberDk` (primary). 90ms. A haptic `TextHandleMove` fires on press.
- **Selection:** the tab underline and the segmented fill **slide** to the new item (`animateDpAsState`, 160ms). A selected card's border fades in.
- **Screen change:** incoming content fades in and slides 24dp from the right (back = from the left), 320ms. Back/title/wallet do **not** animate; they are chrome.
- **Countdown:** each number scales 1.4 → 1.0 with `Snappy` and fades out over the last 200ms. "GO!" is Amber with the kerb strip flashing behind it.
- **Position change in HUD:** the old digit slides up and out while the new one slides in (`AnimatedContent`, 180ms). Gaining a place flashes Ok green for 400ms; losing one flashes Bad red.
- **Reduce motion** (Settings → Accessibility): all slides become 120ms fades; the countdown has no scale.

---

## PART 4 — COMPONENT LIBRARY (`ui/components/DRComponents.kt` — rewrite this file)

Every component below is shown in `00_component_kit.png`. Each one must accept `modifier` and `enabled` and expose `contentDescription` for TalkBack. **No screen may draw its own button, tab, card or toggle.**

| Component | Spec |
|---|---|
| **DRPrimaryButton**(text, icon?, onClick) | `DRShape.Cta`, height 50 (46 in side panels), min width 150. Fill `Amber`, content `OnAmber`. Text = **Kanit Black Italic 17sp ALL CAPS**, icon 20dp, gap 7. Hard shadow: a 3dp offset `Color.Black.copy(.35f)` copy of the shape drawn behind it (no blur). Pressed: `AmberDk`, +2dp Y, no shadow. Disabled: `S4` fill, `TextDis` content. Optional price variant: `"APPLY · 150"`. |
| **DRSecondaryButton** | `DRShape.Card`, height 48, fill `S2`, border 1dp `Line2`. Content: Label style `TextHi` + 19dp icon. |
| **DRGhostButton** | No fill. 18dp `TextMid` icon + Label `TextMid`. Hit area still 48dp. For Reset, Refresh, Lobby. |
| **DRDangerButton** | Same as Primary but fill `Bad`, content white. Only inside a confirmation dialog. |
| **DRBackButton** | 48×48 `DRShape.Card`, fill `S2`, border `Line2`, `arrow_back` 22dp `TextHi`. **Always** at `(Gutter, 14dp)` on every full-screen route. It calls the same function as the system back gesture. |
| **DRCloseButton** | 40×40 visual inside a 48×48 hit area, `DRShape.Control`, fill `S3`, `close` 20dp `TextMid`. **Only** inside dialogs/sheets, top-right, inset 14dp. Never on full screens. |
| **DRScreenHeader**(kicker, title) | Sits right of the back button (x = 76dp). Kicker = LabelS `Amber` (`GARAGE`, `QUICK RACE · SETUP`); title = Title, ALL CAPS, `TextHi`, 3dp gap. |
| **DRWallet**(coins, gems?) | `DRShape.Card` height 36, fill `S1`, coin glyph 16dp + NumM. Top-right of hub screens. Coin changes **count up** over 400ms. |
| **DRTabs**(items, selected) | Height 40, items spaced 18dp, 1dp `Line` baseline. Each item = `NumS "01"` + Label. Selected: `TextHi` + index `Amber` + **2dp Amber underline** that slides. Unselected: `TextLo`, index `TextDis`. |
| **DRSegmented**(options, selected) | `DRShape.Control`, fill `S1`, padding 3, gap 3, height 36. Selected segment fill `Amber` / text `OnAmber`; others `TextMid`. The fill slides. |
| **DRStepper**(label, value, range) | Row height 48 with a 1dp bottom divider. Label in Body; − and + are 36dp `Control` buttons in `S2`; value is NumL, 44dp wide, centred. Long-press repeats. At the limit the button is disabled. |
| **DRToggle** | 46×26 parallelogram (`SkewShape(12f)`). On: `Amber` track with an `OnAmber` 14×18 skewed knob on the right. Off: `S4` track with a `TextLo` knob on the left. Knob slides 160ms. |
| **DRToggleRow**(title, subtitle, checked) | Min height 52, Body + Caption on the left, DRToggle on the right; the whole row is clickable. |
| **DRSlider**(label, value) | Label + NumM value on one line. 4dp `S4` track with an `Amber` active part. Thumb = 14×18 `TextHi` skewed bar with a shadow. 56dp tall. |
| **DRChip**(text, selected, icon?) | Height 32 (hit area 48), `Control` shape. Selected: `Amber` fill. Unselected: `S2` + `Line`. |
| **DRBadge**(text, kind) | LabelS 9.5sp on a parallelogram, padding 6×2. Kinds: `HOST/ON`=Amber, `YOU`=You, `READY`=Ok, `PICKING/FULL`=S4+TextMid, `BOOST`=Boost. |
| **DRMarkerTag**(text) | Highlighter: `Marker` fill, `Ink` text, LabelS, rotated −2°. **Max one per screen.** For "YOUR BEST", "NEW BEST", "NEW". |
| **DRItemCard**(image, name, state) | `Card` shape, 96dp tall, fill `S2`, border `Line`. Thumbnail 88dp wide, centred at the top; name in BodyStrong 11.5sp; status line in NumS ("OWNED" `TextLo`, or coin + price `Amber` when locked). **Selected:** fill `AmberTint`, 1.5dp `Amber` border, an `ON` badge top-right. **Locked:** thumbnail greyscale at 40% brightness. Never put a lock icon over the art; the price says it. |
| **DRSwatch**(color, state) | 40dp tall, `Control` shape. Selected: 2dp `Amber` outline + `check` icon in `OnAmber`. Locked: 55% black overlay + `lock` 16dp. |
| **DRStatBar**(name, value0to1, display) | Grid: LabelS name (70dp) · **10 segments** (2dp gaps, 6dp tall, `Amber` filled / `S4` empty) · NumM value right-aligned. When previewing a different kart, increased segments flash `Ok` and decreased ones `Bad` for 600ms. |
| **DRListRow** (player/result) | Height 40–44, 1dp bottom divider. Position = Kanit Black Italic 22–24sp, coloured Gold/Silver/Bronze for 1–3, `TextMid` otherwise · kart thumbnail 44dp · name BodyStrong · NumM time · NumS gap. **Local player row:** 3dp left edge in `You` + `YouWash` background + `YOU` badge. |
| **DRPanel** | `Panel` shape, fill `S1` at 95% (or `S1` solid on plain backgrounds), border 1dp `Line`. Padding 14–18. |
| **DRDialog**(kicker, title, body, actions) | Width 400, `Panel` shape, `S1`, border `Line2`, shadow `0 24dp 60dp black 60%`, behind a `Scrim`. A 4dp left accent edge in the intent colour (Bad for destructive, Amber for info). Kicker LabelS in the intent colour → Title 26sp → Body `TextMid`, max 2 lines. Actions are bottom-right: the safe action (Secondary) **left of** the risky one (Danger/Primary). DRCloseButton top-right. Back gesture = the safe action. |
| **DRToast**(text, icon, kind) | `Card` shape, height 48, width 300, `S2`, 3dp left edge in the kind colour, 20dp icon in the kind colour, Body 12.5sp. Top-centre, 14dp from the top, 3s, **one at a time** (newer replaces older). Never in the bottom 40% of the HUD. |
| **DRProgress**(value, label?) | Optional LabelS row (label · NumS %). Bar 6dp, parallelogram, `S4` with `Amber` fill. |
| **DRCodeInput**(4 digits) | Four 58×58 `Control` boxes, NumXL-size 30sp digits. Focused box: 1.5dp `Amber`. Opens the numeric keypad automatically. |
| **DRTextField** | Height 48, `Card` shape, `S2`, leading icon `TextLo`. Focus: `Amber` 1.5dp. Error: `Bad` 1.5dp + Caption in `Bad` below. |
| **DRKerbStrip** | 4dp-high repeating 18dp KerbWhite / 18dp Kerb stripes (`drawBehind`). Bottom edge of Main Menu and Garage; top edge of Results. |
| **DRStatusDot** | 8dp circle `Ok` with a 4dp 20% halo, which pulses (alpha 0.2→0.5, 1.2s) **only** while scanning or syncing. |

**Icons:** use `androidx.compose.material:material-icons-extended`, **Rounded** set only (`Icons.Rounded.*`). Sizes are 16 / 18 / 20 / 22 / 24 / 36 dp only.

---

## PART 5 — LAYOUT GRID & RESPONSIVENESS

- The baseline canvas is **915×412dp landscape** (most 20:9 phones). Lock the activity to `sensorLandscape`.
- Wrap every screen in `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` and then apply `Gutter`. Nothing important may sit under a camera cutout.
- **Hub screens** use three zones: the left info column (220–300dp), the centre stage (kart/hero, fills the remainder), and the right `SidePanel` (340dp).
  - Under 800dp width: the side panel becomes 300dp and the stat bars shrink to 90dp.
  - Over 1000dp: the stage grows, panels stay fixed width, and content is capped at 1200dp, centred.
- **Primary CTA** is always anchored **bottom-right** at `(Gutter, 14dp)` from the corner, unless it lives inside a side panel's footer.
- No text may overlap another element at **font scale 1.3**. Long names ellipsize at 1 line (player names ≤ 12 chars are enforced at input).

---

## PART 6 — SCREEN SPECS

Each screen keeps its existing ViewModel and only replaces its composables. Every route has `DRBackButton` + `DRScreenHeader`, except the HUD, Main Menu, Loading and Pause.

### 6.1 Main Menu (`MainMenuScreen.kt`) — `01_main_menu.png`
- **Background:** `room_studio.jpg`, 3dp blur, 45% brightness, plus a horizontal gradient from `Bg` (0%) → 92% (34%) → 15% (62%), so the left column is readable and the kart is vivid.
- **Logo top-left:** "DESK / RACER" in Display 40sp on two lines, then an `LAN` Amber badge + LabelS "SEASON 1 · PAPER CUP" in `TextLo`.
- **Hero:** the currently equipped kart on the turntable, right of centre (~430dp wide), idle rotating at 8°/s. Tap to open the Garage.
- **Bottom-left menu (GT-style, bottom-aligned), width 300:**
  - DRPrimaryButton "QUICK RACE" (296×54, flag icon);
  - then 4 rows of 46dp: index `02–05` NumS `TextDis` · 20dp icon `TextMid` · BodyStrong title + Caption 10.5sp subtitle · `chevron_right` `TextDis`. The rows are Garage, Multiplayer LAN, Shop and Settings.
- **Top-right:** DRWallet (coins + gems), and below it a player chip: LabelS name + `You`-pink skewed "LV 14".
- DRKerbStrip on the bottom edge.

### 6.2 Mode Select — `02_mode_select.png`
- **Four mode cards** in a row: Quick Race, Host LAN, Join LAN, Time Trial. Each card is 210×268, `Panel` shape, `S1`.
  - Top 128dp = that mode's track chase-cam render (desaturated 55% when not selected).
  - A 44dp icon tile overlaps the image edge.
  - Then NumS index, a Title 22sp name, a 2-line Caption, and a footer with a group icon + LabelS player count.
- **Selected card:** 2dp Amber border, grows to 284dp tall (8dp up), full-colour image, Amber icon tile.
- **Footer:** an info line (`info` icon + Caption "LAN works offline — one phone turns on hotspot, others join it.") and DRPrimaryButton "CONTINUE" bottom-right.
- Tapping a card selects it; tapping the already-selected card continues.

### 6.3 Track Select / Race Setup — `03_track_select.png`
- **Left: track hero**, 520×250 `Panel`, using the track's chase-cam render.
  - Bottom gradient to `Bg`; a difficulty badge + MarkerTag "YOUR BEST 1:12.904" (only if a best exists).
  - Track name in Display 38sp, then a NumS meta row with icons (length, turns, boost pads).
  - Chevron buttons (40×48 `Control`, 70% `Bg`) sit at the vertical centre of each side. Swipe also works.
- **Below the hero:** a thumbnail strip of all 8 tracks (58×42 `Control`; selected = 2dp Amber, others 55% brightness).
- **Right SidePanel "RACE RULES":** Laps stepper (1–9), Bots stepper (0–5), Bot skill segmented (Easy/Normal/Pro), Items toggle row.
- **CTA:** "START RACE" (200×50, flag).

### 6.4 Garage (`GarageScreen.kt`) — `04_garage_body.png`, `05_garage_paint.png`
- **Background:** `room_workshop.jpg`, blur 8, brightness 38%, saturation 60%, plus a horizontal gradient that darkens the left 38% and right 30%.
- **Stage:** the 3D kart on `garage_turntable.png`, centred at x≈40%, filling ~390dp. Drag rotates it (with inertia); it auto-rotates after 3s idle. Caption "Drag to rotate" with a `360` icon at the bottom-left, which hides after the first drag.
- **Left column:** Display 33sp kart name (ALL CAPS); a Caption subtitle (Body tab = flavour line, Paint tab = "Lime gloss · white trim"); 4 DRStatBars (Speed, Accel, Handling, Weight).
- **DRWallet** at the top, x = 388dp, so it never collides with the panel.
- **Right SidePanel**, 344dp, top 12 / bottom 16. Inside: DRTabs `01 BODY · 02 PAINT · 03 LIVERY · 04 DRIVER`, then the tab content, then a footer row with Ghost "RESET" + Primary.
  - **Body tab:** a 3-column DRItemCard grid (gap 8). Footer CTA = "EQUIP", or "BUY · 800" if locked, or disabled "EQUIPPED" if already on.
  - **Paint tab:**
    - LabelS "PRIMARY · {COLOUR NAME}", then a 6-column swatch grid (12 colours);
    - LabelS "FINISH", then a Segmented of Gloss/Matte/Metal/Pearl;
    - an accent row with 4 small skewed swatches.
    - Footer CTA "APPLY · 150" (it shows the cost of the changes).
  - **Livery tab:** the same ItemCard grid with livery thumbnails.
  - **Driver tab:** the same grid with driver portraits, plus a name DRTextField at the top.
- **Behaviour:** selecting an item previews it live on the stage and diffs the stat bars, but **does not commit** until the CTA is pressed. Leaving with uncommitted changes shows a DRDialog: "Discard changes?" with Keep editing / Discard.

### 6.5 Host Lobby (`HostLobbyScreen.kt`) — `06_lobby_host.png`
- **Left panel (300w):**
  - LabelS "JOIN CODE", then the code in NumXL 44sp with spaced digits;
  - a hotspot status line (`wifi_tethering` in Ok + Caption with the IP);
  - a divider, then LabelS "RACE": a track thumbnail + name + NumS "3 LAPS · ITEMS ON · NORMAL BOTS";
  - Secondary "CHANGE".
- **Right panel (567w):** a header row with LabelS "PLAYERS 4/6" and the Ok-coloured "3 READY". Then 6 DRListRows:
  - filled rows show index, kart thumbnail, name (+"(you)" caption for the host), ping (wifi icon + NumS ms), and a badge (HOST / READY / PICKING);
  - empty slots have a dashed divider, `person_add` in `TextDis`, and the Caption "Open slot · bot fills if empty".
- **Below:** an hourglass + Caption "Waiting for Kabir…". "START RACE" is **enabled** even if not everyone is ready; pressing it with unready players shows the dialog "Start without Kabir? A bot will take his slot."

### 6.6 Join LAN (`JoinScreen.kt`) — `07_lobby_join.png`
- **Left:** LabelS "SCANNING THIS WI-FI" with a pulsing StatusDot, plus a Ghost "REFRESH".
- **Room rows** (62dp, `Card` shape): signal icon (Ok when strong, Amber when weak), room name BodyStrong 14sp + track Caption, NumL "4/6", badge LOBBY (Ok) / FULL (muted). Full rooms are at 50% opacity and disabled. The selected row gets the Amber border and tint.
- **Right panel "JOIN BY CODE":** DRCodeInput + a help Caption + an error line in `Bad` when the device isn't on a hotspot/Wi-Fi ("Not on a hotspot? Open Wi-Fi settings" deep-links to `Settings.Panel.ACTION_WIFI`).
- **CTA:** "JOIN ROOM" (login icon).
- **Empty state:** after 6s with no rooms, show the Caption "No rooms yet. Ask the host to open the lobby, then tap Refresh."

### 6.7 Race HUD (`hud/HudLayout.kt` and siblings) — `08_hud_race.png`, `09_hud_countdown.png`
Five persistent groups. **The centre 40% of the screen width and the middle 50% of the height stay empty.**
1. **PositionBadge (top-left 20,10).**
   - The position number is DisplayXL 72sp, with the suffix ("RD") in Title 24sp and "/6" in NumL `TextMid`. Colours: Gold / Silver / Bronze for 1–3, `TextHi` otherwise.
   - Below it: LabelS "LAP" `TextMid` + NumL "2/3" ("/3" smaller, `TextMid`) + NumL lap time `1:24.381`.
   - Below that: a coin glyph 18dp + NumL two-digit coin count "07".
   - All HUD text gets a hard shadow: `Shadow(Color.Black.copy(.55f), Offset(0f, 2f), 0f)` plus a soft one at 12px blur, 45%.
2. **Event feed (left, y=150):** at most 2 lines, each on a `HudPlate2` strip with a 2dp `You` left edge. Format: item icon 20dp + "You" in `You` + verb in `TextMid` + target name. Each line fades after 2.5s.
3. **Pause + Minimap (top-right):**
   - Pause is a 48dp `Card` shape on `HudPlate2` with a 18%-white border.
   - Minimap is 124×96 `HudPad` on 42% `Bg`. The track line is 5dp white at 55%. Rivals are 4dp dots (leader Gold, 2nd Silver, others white); **you** are a 6dp `You` dot with a 2dp white ring, drawn last.
4. **Speed + drift meter (bottom-centre):**
   - A 180dp-wide plate (parallelogram, `HudPlate2`) holding NumXL italic speed + LabelS "KM/H".
   - Under it, a 5dp skewed `Boost` bar that glows (`Boost` shadow 8dp), with LabelS "DRIFT BOOST" in `Boost`.
   - The plate hides when Settings → "Show speed" is off.
5. **Controls:**
   - **Left:** two 76×76 `HudPad` steer pads (`arrow_back_ios_new` / `arrow_forward_ios` 36dp) on `HudPlate` with a 22% white border, 10dp apart.
   - **Right, gap 22dp:** the **Item button** is a 76dp `HudSkew` square on 55% `Bg`.
     - Ready: 2dp `Amber` inner border + 18dp Amber glow, with the item icon (64dp) inside.
     - Empty: 1dp 22% white border with a LabelS "EMPTY" in `TextLo`.
     - Rolling (roulette): the icons cycle every 70ms for 1.2s, then a 120ms scale pop.
   - The 66×58 BRAKE pad sits under the item button, and the 96×144 **GAS** pedal (`HudSkew`) is at the far right with a vertical Amber gradient at 38→18% and an 80% AmberHi border.
   - Opacity follows Settings → Button opacity (default 38%). Pressed controls go to 70%.
   - **The item lives ONLY in the item button.** There is no other item display anywhere on the HUD.
- **Countdown state:** controls drop to 35% opacity. A horizontal band (`Bg` at 75%, fading at both ends) sits behind a DisplayXL **150sp** Amber number, with LabelS "HOLD GAS ON "GO" FOR A ROCKET START" below.
- **Final lap:** a banner "FINAL LAP" (Display 40sp) slides in from the right on a kerb-striped band for 1.6s.
- **Finish:** "FINISH" banner + position in DisplayXL. After 1.5s, transition to Results.
- **Wrong way:** a centred DRToast-style plate in `Bad` reading "WRONG WAY" (Label) with a `u_turn_left` icon. This is the only element allowed in the centre, and only while it's true.

### 6.8 Pause — `10_pause.png`
- **Background:** the frozen game frame, blurred 6dp at 50% brightness, plus a 50% scrim.
- **Left column at (48, 44):**
  - LabelS Amber "STUDY DESK · LAP 2/3", then Display 56sp "PAUSED";
  - DRPrimaryButton "RESUME" (260×54);
  - 3 rows (46dp, divider): Restart race (host/offline only, hidden for LAN clients), Settings, Leave race (`Bad` icon + text → confirmation dialog).
- **Caption at the bottom:** in LAN, "LAN race keeps running — you'll rejoin where your kart is."
- **Right panel "STANDINGS"** (260w): six rows of 36dp, using DRListRow styling.

### 6.9 Results (`ResultsScreen.kt`) — `11_results.png`
- **Top:** DRKerbStrip on the top edge. Kicker "STUDY DESK · 3 LAPS", headline Display 44sp "3RD PLACE" (or "WINNER!" in Gold when 1st), and a MarkerTag "BEST LAP 1:12.090" if it was a personal best.
- **Table panel** (540w): a header row of LabelS 9.5sp in `TextLo` (POS · DRIVER · TIME · GAP), then 6 DRListRows. DNF shows "DNF" in `TextLo` with an empty gap cell.
- **Rows enter one by one** (60ms stagger, slide 16dp + fade), from 6th up to 1st.
- **Rewards panel** (322w): rows "Finish · 3rd +120" and "Coins picked up +07", with values in NumL Amber that count up. Then a DRProgress "LV 14 → 15 · +340 XP" that animates after the counts finish.
- **Actions bottom-right:** Ghost "LOBBY" (LAN only), Secondary "REMATCH", Primary "NEXT RACE".

### 6.10 Settings (`SettingsScreen.kt`) — `12_settings.png`
- **Left rail** (220w panel `S1`): 5 categories × 48dp (index NumS · 20dp icon · BodyStrong): Controls, Audio, Graphics, Network, Accessibility. Selected: `AmberTint` + 3dp Amber left edge.
- **Content column** (320w) of DRSegmented / DRSlider / DRToggleRow rows:
  - **Controls:** Steering (Buttons/Tilt/Wheel), Steering sensitivity, Button opacity, Auto-accelerate, Left-handed layout.
  - **Audio:** Master, Music, SFX, Engine sliders; UI sounds toggle.
  - **Graphics:** Quality (Low/Med/High), FPS cap (30/60), Shadows toggle, Motion blur toggle.
  - **Network:** Player name (DRTextField), Show ping toggle, Port (advanced, collapsed).
  - **Accessibility:** Reduce motion, High-contrast HUD (plates → 75%), Colour-blind safe ranks (adds ①②③ shapes), Text size preview.
- **Right column "PREVIEW"** (296w): a live mini HUD preview on the chase-cam image that reflects the opacity, left-handed and speed settings instantly. Below it, Haptics and Show speed toggle rows.
- Changes save immediately (no Save button). The toast "Saved" appears only on the first change per visit.

### 6.11 Shop / Desk Pass (`ShopAndPassScreens.kt`) — `13_shop.png`
- **Header:** kicker "SHOP", title "SEASON 1 · PAPER CUP", plus the wallet with gems.
- **Left Desk Pass hero card** (330×316 `Panel`): a diagonal gradient `AmberTint` → `S1`, a 1.5dp Amber border, the hero kart image, and a "DESK PASS" badge.
  - Display 24sp "30 TIERS · COSMETIC", with the Caption "Karts, paints, stickers. No pay-to-win — stats never change."
  - DRProgress "TIER 7/30" next to DRPrimaryButton "₹149" (localized price from Play Billing).
- **Right:** DRTabs `01 FEATURED · 02 KARTS · 03 PAINT · 04 LIVERY`, then a 3×2 grid of shop cards (118dp tall). Each card has a type badge top-left (muted), a centred thumbnail, and a bottom row with the name in BodyStrong and coin + NumM price (or "OWNED" in `TextLo`).
- Tapping a card opens a **purchase sheet** (DRDialog variant): big preview, price, and Primary "BUY · 800" / Secondary "CANCEL". With insufficient coins the Primary is disabled and a Caption reads "You need 350 more coins".
- **Honesty rules:** no fake timers, no "only 2 left", and real-money prices always show the currency.

### 6.12 Loading / Player Sync — `14_loading.png`
- **Background:** a full-bleed chase-cam render of the track at 60% brightness, plus a bottom gradient to `Bg`.
- **Bottom-left:** difficulty badge + LabelS "3 LAPS · 6 RACERS", then the track name in Display 52sp on up to 2 lines.
- **Bottom-right:** a TIP block (LabelS Amber "TIP" + Body `TextMid`, 300w). Highlighted words are BodyStrong in their semantic colour (e.g. **cyan** in `Boost`). Rotate through 12 tips.
- **Very bottom:**
  - a row with LabelS "SYNCING PLAYERS 4/5" + 5 skewed 8dp pips (Ok when synced, `S4` otherwise) and NumS "78%" on the right;
  - then a full-width 6dp skewed DRProgress.
- Never show a spinner. If sync takes over 10s, show a DRToast (warn): "Waiting for Kabir… (slow connection)".

### 6.13 Dialogs & Toasts — `15_dialog_toast.png`
- Use DRDialog for: Leave race, Discard garage changes, Start without players, Purchase, Host migration failed, and Kicked from room.
- Use DRToast for: player joined/left (Ok/warn), host migrating (warn), connection retry (Bad with count), item hit you (You), saved, and coins awarded.
- **Never** use Android `Toast`, `AlertDialog`, `Snackbar` or Material `ModalBottomSheet` defaults.

### 6.14 How To Play overlay (`HowToPlayOverlay.kt`)
- 3 swipeable cards in `Panel` shape over a scrim: Steer & Drift, Items, LAN Hotspot. Each has a Display 32sp title, a mini illustration (HUD component crops) and 2 lines of Body.
- Indexed pager pips (`01 02 03`, LabelS; the selected one is Amber with an underline).
- Ghost "SKIP" at the top-right; Primary "GOT IT" on the last card.

---

## PART 7 — COPYWRITING RULES

- **Screen titles and hero names:** ALL CAPS, Kanit (`CHOOSE MODE`, `CLASSIC TOY`).
- **Card titles:** Title Case, Kanit (`Host LAN`).
- **Labels and buttons:** ALL CAPS via `DRLabel` / `DRPrimaryButton` (`START RACE`, `EQUIP`). Max 2 words. Verb-first.
- **Body:** sentence case, ≤ 2 lines, no exclamation marks except "WINNER!" and "GO!".
- **Numbers:**
  - Thousands separators (`2,450`). Times `m:ss.SSS`. Gaps `+1.703`.
  - Ordinals as `1ST 2ND 3RD 4TH` in the HUD/results.
  - Ping `18ms`. Prices use the coin glyph and never the word "coins" (except in Captions).
- **Errors** say what happened and what to do: "Not on a hotspot? Open Wi-Fi settings", never "Error 3".
- **Player-facing words:** "kart" (not car), "room" (not session), "host" (not server), "hotspot" (not AP).

---

## PART 8 — ACCESSIBILITY & FEEL

- **Contrast:** Body text ≥ 4.5:1 on its background (`TextMid` on `S1` passes, but `TextLo` is only for meta and captions ≥ 11.5sp). Amber CTA text `OnAmber` on Amber = 10.9:1.
- Every interactive element is ≥ 48×48dp, with ≥ 8dp between adjacent targets (HUD steer pads 10dp, item↔gas 22dp).
- Never encode state with colour alone. Selected tabs have the underline, selected cards have the `ON` badge, rank colours come with numbers, and the "you" row has a `YOU` badge.
- **TalkBack:** HUD reads position/lap only on change ("Third place, lap two of three"). Cards read "Classic Toy, owned, equipped" / "Phantom, locked, 800 coins".
- **Haptics:** CTA press = light tick; item hit you = heavy; overtaken/overtaking = double light; countdown GO = medium.
- **UI sounds:** tab/selection tick, CTA confirm, back whoosh, purchase cash-register, error buzz. All are on the SFX bus at −12 dB relative to engine.

---

## PART 9 — QA GATES (add these tests; the build must pass them)

1. **Token lint unit test** (`DesignTokenLintTest`): it scans `app/src/main/java/**/ui/**/*.kt` (excluding `ui/theme/`) and fails on:
   - `Color(0x`
   - `RoundedCornerShape(` (allowed only in `Coin.kt`, `StatusDot`, `Minimap.kt`)
   - `FontFamily.Default`
   - `MaterialTheme.typography`
   - a raw `\d+\.sp` outside `Type.kt`
   - `Toast.makeText`
   - `AlertDialog(`
2. **Touch-target test** (Robolectric + Compose UI test): render each screen at 915×412dp. Every node with a click action has bounds ≥ 48×48dp.
3. **HUD overlap test:** render `HudLayout` at 915×412, 800×360 and 1080×480, with font scale 1.0 and 1.3. Assert:
   - no two of {PositionBadge, Pause, Minimap, SteerLeft, SteerRight, Item, Brake, Gas, SpeedPlate} have intersecting bounds;
   - none intersect the centre safe rect (30%–70% of width, 25%–75% of height).
4. **Single-CTA test:** each screen has exactly one node tagged `cta_primary`.
5. **Back-button test:** every full-screen route has a node tagged `nav_back` whose bounds start within 24dp of the top-left safe inset. DRCloseButton (`nav_close`) appears only inside dialogs.
6. **Tabular-numbers test:** `DRType.NumL.fontFeatureSettings == "tnum"` and the font family is BarlowSC. A timer string "1:11.111" and "8:88.888" measure the **same width**.
7. **Screenshot list for manual review** (log the paths): Main, Mode, Track, Garage Body, Garage Paint, Host Lobby, Join, HUD, Countdown, Pause, Results, Settings, Shop, Loading, Dialog. Compare them against `design/v6/*.png` side by side.

---

## PART 10 — IMPLEMENTATION ORDER (do it in this order and keep the app compiling after each step)

1. Add the fonts to `res/font`. Create `Color.kt`, `Type.kt`, `Shapes.kt`, `Dimens.kt` and `Motion.kt` as above. Make `Theme.kt` set the window background to `DR.Bg`.
2. Rewrite `DRComponents.kt` with every component in Part 4. Add `@Preview` for each one with all its states.
3. Re-skin the screens in this order: Garage → HUD (+ countdown/final lap/finish/wrong way) → Main Menu → Mode Select → Track Select → Host Lobby → Join → Pause → Results → Loading → Settings → Shop → How To Play → dialogs/toasts.
4. Delete the old one-off styles (glass cards, gradient pills, ad-hoc colours) and run the token lint test until it is clean.
5. Add the QA tests from Part 9.
6. **Do not** change gameplay constants, networking or the asset formats. If a screen needs data it doesn't have (e.g. track turns/boost-pad count), read it from the track JSON (`props`, `decals`, `controlPoints`) or compute it, and never hardcode it.

**Definition of done:**
- Every screen matches its mockup's composition.
- Every value comes from tokens.
- Every live number is tabular Barlow.
- One amber CTA per screen.
- The back button is in the same place everywhere.
- The HUD centre is clear.
- All tests pass.
