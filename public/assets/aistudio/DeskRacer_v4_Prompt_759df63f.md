DESK RACER LAN — v4 "PRODUCTION READY"
Master prompt for Google AI Studio. Paste all of it as one message. It builds on the current v3 project.

You are the lead engineer, network engineer, technical artist and UX lead shipping Desk Racer LAN to the Play Store. v3 looks much better, but it has real bugs in multiplayer and the UI isn't polished enough. Every bug below was found by reading the current code. Fix each one at its ROOT, then polish every screen, control and visual to production quality. Where this spec gives exact values, formats or file names, use them exactly.

====================================================================
PART A — HARD RULES
====================================================================
A1. Do NOT modify `signingConfigs`, `signingConfig` lines, `applicationId`, `namespace` or the plugin block. minSdk 24, targetSdk 36, `sensorLandscape`, immersive.
A2. Gameplay is 100% offline (LAN sockets only). Billing and ads stay behind the existing fake gateways.
A3. ASSETS: the build pipeline overwrites `app/src/main/assets/` with asset pack v4 and `res/mipmap-*` + `res/font/poppins_*` with the brand files. Never delete or regenerate them. Missing files fall back to procedural content without crashing (the AI Studio preview may hold the older pack).
A4. ASSET CONVENTIONS (v4 — read carefully):
   - DRM2 texture coordinates use a TOP-LEFT origin (v = 0 is the first, top row of the image). Upload Android Bitmaps exactly as decoded, with no flip in code or shader. v3 textures appeared upside down because the old pack used the opposite origin; the pack is now fixed, so remove any flip you added to compensate.
   - Every texture and every DRM vertex colour is sRGB. Convert to linear before lighting (on ES 3.0 upload colour textures as GL_SRGB8_ALPHA8; convert vertex colours with pow(c, 2.2) or the exact sRGB curve in the vertex shader). Apply tone-mapping and gamma ONCE at the end. Mixing these up makes everything look washed out or too dark.
   - Normal maps (`*_n.jpg`) are LINEAR, not sRGB. Tangent-space with +Y up (OpenGL convention).
A5. Compile after every phase. No TODO stubs, no `!!` on runtime-nullable values, no allocations in the 60 Hz loops, no I/O on the UI or GL threads.

====================================================================
PART B — MULTIPLAYER: ROOT CAUSES AND THE FIX (highest priority)
====================================================================
What is broken in the current code, and why:
 B-1 "Only the host gets items": `LanHost.broadcastWorldSnapshotUdp` sends only kart pose (x, y, z, heading, speed, sparkTier, boosting). Item boxes are picked up only on the host (`ItemsEngine.stepProjectilesAndPickups` runs only when `isHostAuthority`), which writes `pendingItem` into the HOST's copy of the client kart and never sends it. Clients never receive an item, never see box/coin states, projectiles or hits, and are never spun out. `OP_ITEM_ACTION` is defined but `LanHost.handleClientControlFrame` ignores it, and `RaceSession.onLocalClientItemAction` is never assigned. Client-fired items exist only on the client.
 B-2 "Rankings go haywire": every phone computes positions independently. The client derives remote karts' progress from 100 ms-old interpolated poses, and in `updateInterpolatedRemoteKarts` a progress jump over 25 u (packet gap, teleport snap) is IGNORED, so that kart permanently loses progress and stays ranked too low. Remote laps and finish order are counted separately on each phone (`handleKartLapCompleted` with each phone's own `nextFinishPositionToAssign`), and the host never broadcasts positions or laps. Ties flicker because sorting uses raw floats with no hysteresis. The HUD therefore disagrees with the final host results.
 B-3 `LanClient.lastWorldSeq` is never reset. After joining a second room, or when the host restarts its room for a rematch, every new snapshot is dropped as "old" and remote karts freeze.
 B-4 The countdown ignores the host clock: the client reads `startAtHostMs` in `OP_START_COUNTDOWN` but never uses it, and the ping/pong offset is never applied.
 B-5 The speed class (50/100/150cc), bot count and bot difficulty are not in the start frame or lobby state. Clients race at their OWN local class, so speeds differ between phones.
 B-6 Network callbacks (`onCountdownStartedFromHost`, `onHostLeftRoom`, …) run on IO threads and write Compose state (`currentScreen`) and RaceSession directly. This causes races and crash risk.
 B-7 `OP_REMATCH` is defined but unused, so multiplayer rematch doesn't work.
 B-8 Every UDP send does `scope.launch { … }` (30+ coroutines a second), so packets reorder and the host drops them as out-of-sequence.
 B-9 After a REMOTE human finishes, the host also runs the CPU autopilot on that kart while still applying the client's packets, so it jitters.

THE FIX — one host-authoritative model, implemented in `net/` + `RaceSession`:
B1. SESSION: the host creates a random 32-bit `raceId` per race and per rematch. Every TCP frame and UDP packet carries `protocolVersion` (bump to 4) and `raceId`. Receivers drop packets from a different raceId. Reset ALL sequence counters on connect, on new raceId, and on disconnect. Sequence comparison is wrap-safe ((a − b) as Int > 0).
B2. AUTHORITY:
   - Each client simulates ONLY its own kart's motion (instant input), and sends ClientKartState at 30 Hz: seq, raceId, clientTimeMs, pose, speed, sparkTier, boosting, drifting, steer, plus an `inputEdges` byte (itemPressed, itemBackward, itemHeld). The edges are repeated in 3 consecutive packets so one lost packet doesn't eat a press.
   - The HOST owns everything else: item box pickups and the item roll, coins, item use, projectile spawn and flight, hits/spin-outs/shields, hazards, laps, race positions, finish order, DNF, results, rematch.
B3. WORLD SNAPSHOT v4 (host → each client, UDP, 30 Hz, ≤ 1200 bytes):
   header: type, protocolVersion, raceId, seq, hostRaceTimeMs, phase
   per kart (≤ 8): slot, x, y, z (float), heading (int16 scaled), speed (int16 ×100), flags (boosting, drifting, sparkTier, spinningOut, invulnerable, finished, respawning, shield), heldItem (byte), rouletteRemainingMs (uint16), coins (byte), lap (byte), racePosition (byte), raceDistance (float)
   item boxes: bitmask of active boxes (uint32) · coins: bitmask (uint32, or two words if more than 32)
   projectiles (≤ 16): id (uint16), kind, owner, x, y, z, heading
   hazard phase: float seconds (the deterministic hazard clock)
B4. RELIABLE EVENTS (TCP, ordered; also mirrored in the snapshot so a late joiner self-corrects):
   ITEM_GRANTED(slot, item, rouletteMs) · ITEM_USED(slot, item, backward) · PROJECTILE_SPAWNED(id, kind, owner, pose) · HIT(victim, attacker, kind) · COIN(slot, total) · LAP(slot, lap, lapTimeMs) · FINISHED(slot, position, timeMs) · RACE_END_COUNTDOWN(secondsLeft) · RESULTS(list) · REMATCH(raceId, settings).
   The client applies these to its own kart: a granted item starts the roulette UI and sets heldItem, and a HIT spins out the local kart immediately with sound, haptic and toast.
B5. CLIENT ITEM FLOW: a client presses ITEM → plays the throw animation and sound immediately (prediction) → sends the edge → the host validates (holds that item, not spun out), spawns the projectile from the client's latest pose advanced by half the RTT, and broadcasts it. If the host rejects, the client quietly restores the HUD item. Box pickup works the same way: the client may hide the box and start the roulette locally, the host confirms with ITEM_GRANTED within 150 ms, otherwise the client rolls back. The client never decides the item itself.
B6. POSITIONS ARE ONE TRUTH: the HUD position, lap, standings ladder, minimap order, paper-plane targets and item odds ALL use the host's `racePosition` and `lap`. Clients do not count laps for remote karts at all. Ranking on the host is by (finished first, by finish order) then raceDistance, with a hysteresis of 0.75 u: positions swap only when the gap exceeds 0.75 u for 120 ms. Ties are broken by lower slot.
B7. PROGRESS ROBUSTNESS (host and solo): never ignore large progress jumps. If |deltaS| > 25 u, re-seat the kart with a windowed spline search (±60 samples around the last index, height-matched) and add the true signed delta when the kart is moving forward. Only a confirmed respawn can move raceDistance backwards (to its respawn sample).
B8. CLOCK: use 7 ping/pong samples and take the median RTT to get `offset = hostTime − clientTime`. The countdown starts at `startAtHostMs − offset` locally, so every phone shows 3-2-1-GO within ±20 ms. Race time on clients = hostRaceTimeMs + (now − snapshotReceiptTime).
B9. SETTINGS SYNC: lobby state and the start frame carry track, laps, itemMode, ccClass, botsEnabled, botCount, botDifficulty, and every slot's name/colour/body/livery. Clients use ONLY these values, never local prefs, for the race.
B10. THREADING: all network callbacks post to a single-threaded `GameCommandQueue` that the sim thread drains at the start of each tick. UI state changes go through `withContext(Dispatchers.Main)` / a MainScope. No Compose state is written from IO threads.
B11. UDP SENDING: one long-lived sender per socket that drains a Channel(capacity = 4, DROP_OLDEST), so there's no coroutine per packet. Reuse the byte buffers.
B12. REMATCH and LOBBY: implement OP_REMATCH (host picks "Race again" → new raceId → everyone back to the lobby with ready checks, players kept). A client that drops mid-race can rejoin within 30 s with its stable playerId and reclaims its slot (a bot drove it in the meantime). Remote humans who have finished are driven by their OWN phone's autopilot (the host never autopilots a remote human), which fixes B-9.
B13. BOTS IN MULTIPLAYER: the host runs them, and they appear in snapshots like any kart. Bot count and difficulty are host settings shown in the lobby.
B14. NETWORK TEST HARNESS (must pass, JVM tests in `app/src/test`): run one host and two clients in-process over a FakeNetwork (60 ms ± 20 ms latency, 5% loss, 2% reordering, 1% duplicates) for 3 laps with 3 bots.
   Assert:
   (a) both clients receive and use items at least 5 times each;
   (b) projectiles fired by clients hit host-side karts and vice versa;
   (c) at every snapshot after the first second, every client's displayed racePosition for every kart equals the host's within 300 ms;
   (d) final RESULTS are byte-identical on all three peers;
   (e) a rematch with a new raceId works, and old-raceId packets are ignored;
   (f) a client disconnecting at lap 2 becomes a bot, rejoins and gets its slot back.

====================================================================
PART C — RACE LOGIC POLISH (solo and multiplayer)
====================================================================
C1. Position display: animate changes (slide plus a 1.15× pulse), never more than one change per 120 ms, and show the "▲3 / ▼4" chip only for changes that last ≥ 400 ms (no flicker chips).
C2. Lap display never shows "4/3": cap at totalLaps, and show "FINISHED" after the line.
C3. Finish order: the line crossing time is interpolated between ticks (sub-tick precision) so near-simultaneous finishes are ordered correctly. Ties go to the earlier interpolated time, then the lower slot.
C4. Results: a DNF is placed after every finisher, ordered by raceDistance. Times show as m:ss.mmm. Best lap is marked ★ per racer. NEW BEST only for a real personal record.
C5. Respawn: always on the road centreline facing the tangent, 10 u behind the last good sample, with 1.5 s ghost. Never inside a prop (check prop clearance before choosing the point).
C6. Item odds use the host's position. "Last place" means the last among unfinished karts.
C7. Wrong-way detection ignores the first 2 s after a respawn or spin-out.

====================================================================
PART D — VISUALS: USE THE v4 PACK FULLY
====================================================================
D1. Apply A4 (UV origin, sRGB→linear). This alone fixes the "doesn't look real" textures from v3.
D2. Normal maps: the desk surface and road use `surfaceNormal` / `roadNormal` from the track JSON, with tangent = world +X for the desk plane and the spline tangent for the road. Strength 0.8 (Medium), 1.0 (High), off on Low.
D3. Decals: the track JSON now has `decals: [{texture, x, z, yawRad, size}]` (coffee rings, sticky notes, paint splats, dust scuffs). Render them as flat quads 0.05 u above the desk with polygon offset, alpha blended, sorted, receiving shadows and never on the road. Also use `textures/decals/skid_mark.png` for drift skid trails (ring buffer of 2000 quads, fading over 20 s), `boost_chevrons.png` on boost pads, and `grid_box.png` on start grid slots.
D4. New props in v4: punctual_lamp (arched lamp), iridescence_lamp (table lamp), flight_helmet, boombox, sneaker, fox_toy, barramundi_fish, velvet_sofa, damask_chair. They are already placed in the track JSONs (Study Desk, Gamer Desk, Kitchen Counter, Bookshelf Spiral, Bathtub Rim, Toy Box, Midnight Office). velvet_sofa and damask_chair are for the Main Menu room scene and the Garage background, not tracks.
D5. HUD item icons: use `textures/ui/items/<item>.png` (256 px, transparent, rendered from the real models: battery, thumbtack, eraser, marble, marble_homing, paper_plane, magnet, star_sticker, triple_tacks, triple_battery) in the item slot, the roulette, the shop and the tutorial. Remove any vector/emoji item icons.
D6. Kart thumbnails: `textures/ui/karts/<body>.png` (512×320, transparent) for the Garage body picker, the Shop and lobby player cards.
D7. Track cards: `previewCard` from each track JSON (`textures/ui/tracks/<slug>.jpg`, 640×360, with the real mini-map drawn on the track's surface) in Track Select, the loading screen and the lobby.
D8. Look target: match the reference renders in `refs/*_chase.jpg` and `refs/*_top.jpg` (rendered from this exact pack: macro-photo desk, blurred real room behind, real objects at real scale). If they aren't in the workspace, aim for that description.
D9. Shadows: every prop and kart casts into the shadow map. Props also get the 0.6 u contact-AO decal from v3.
D10. Performance: instance repeated pylons; build LOD1 (50% triangles) for props over 6k triangles at load; keep ≤ 250k triangles on High.

====================================================================
PART E — GARAGE REBUILD (the car must be the hero)
====================================================================
Root cause: the showroom camera centres the kart in the FULL viewport, while the settings panel covers the right 54% and the stats card covers the bottom-left, so the kart sits behind the UI.
E1. Layout (landscape): the LEFT 60% of the safe area is a full-bleed 3D stage. The RIGHT 40% (min 300 dp, max 420 dp) is a drawer card with tabs: BODY · PAINT · LIVERY · NAME. The top bar has Back (left), "Garage" title, and a Save/Equip primary button (right).
E2. Camera framing: compute the stage rect in pixels and put the kart's bounding-sphere centre at the stage centre using an off-centre projection (shift the projection matrix: lensShiftX = (stageCenterX − screenCenterX) / (screenWidth/2)). Pick the distance so the kart's bounding sphere fills 70% of the stage height. Automated test: project the kart's 8 bounding-box corners and assert they lie inside the stage rect at 640×360, 800×360 and 915×412 dp.
E3. Stage: the kart turns on `textures/ui/garage_turntable.png` (a glowing rim disc), with a soft key light and rim light, the room_studio backdrop blurred, and velvet_sofa / damask_chair far behind and out of focus. Drag to rotate (inertia), pinch to zoom (0.8–1.4×), double-tap to reset, slow auto-rotation that pauses for 4 s after interaction.
E4. Stats: a compact overlay in the stage's bottom-left corner (max 200×88 dp, 60% opaque), with 4 thin bars and a delta preview (+/− arrows) when hovering another body. Collapsible.
E5. BODY tab: a horizontal carousel of kart thumbnails (D6) with locked/owned badges. PAINT tab: 12 swatches plus finish (Gloss / Matte / Metallic / Pearl → roughness/metalness uniforms), live on the 3D kart. LIVERY tab: 3 liveries per body. NAME tab: a text field (max 12, profanity-safe basic filter), with the keyboard pushing the drawer, not the stage.
E6. Every change previews instantly on the 3D kart, and Back without saving asks "Discard changes?".

====================================================================
PART F — HUD: SMALLER, CLEANER, ALWAYS READABLE
====================================================================
F1. HUD Scale setting: 70–130%, default 90% (default 80% if the safe height is under 380 dp). It applies to every HUD element and to the action buttons (separately adjustable in the controls editor).
F2. Coverage budget: opaque HUD pixels must cover at most 12% of the screen and translucent panels at most 22%. Panels use 45% black glass (#000 at 0.45) with a 1 dp white 12% border. No big solid cards over the road. Add a debug readout of the coverage % in Developer settings and a unit test computing it from node bounds.
F3. Zones (replace v3 values): TOP_LEFT 0–24% w × 0–18% h · TOP_CENTER 38–62% w × 0–11% h · TOP_RIGHT 76–100% w × 0–20% h · LEFT_MID 0–14% w × 24–52% h (minimap 84–104 dp) · RIGHT_MID 88–100% w × 24–58% h · BOTTOM_LEFT 0–38% w × 56–100% h (steer) · BOTTOM_CENTER 43–57% w × 84–100% h · BOTTOM_RIGHT 64–100% w × 58–100% h (actions) · CENTER 30–70% w × 22–64% h.
F4. Element sizes at 100%: position 44 sp ExtraBold + "/8" 14 sp; lap 13 sp; timer 15 sp tabular; item slot 64 dp; speedometer 120×32 dp; DRIFT button 84 dp; ITEM 68 dp; BRAKE 52 dp; pause 40 dp icon in a 56 dp hit area.
F5. Contextual hiding: speed label hides after 3 races; the standings ladder collapses to the top 3 + you; banners fade after 1.6 s; nothing permanent in CENTER.
F6. Text shadows (0, 1 dp, 3 dp blur, 60% black) on all HUD text for sunlight readability.

====================================================================
PART G — CONTROLS: PRODUCTION FEEL
====================================================================
G1. Pointer bug: `SteerZone` and `ActionButtons` read `event.changes.firstOrNull()`, which is not the pointer that started the gesture, so a second finger can hijack steering. Track the `PointerId` from `awaitFirstDown` and only read that pointer's changes. Release when that pointer goes up or is cancelled.
G2. Edge gestures: call `View.setSystemGestureExclusionRects` (API 29+) for the steer zone and action buttons (Android caps this at 200 dp of vertical edge per side, so prioritise the lower part of each edge). This stops the system back gesture from triggering while steering on the left edge.
G3. Input timing: sample the input at each sim tick (not each frame), keep press edges in an atomic queue so a tap shorter than one tick is never lost, and give the drift hop a 120 ms buffer (a press just before landing still counts).
G4. Steering feel per scheme:
   - Smart: dead zone 6 dp, full lock 64 dp, curve x^1.5, return-to-centre smoothing 70 ms;
   - Pro: x^1.3, sensitivity 0.6–1.6;
   - Tilt: game rotation vector, ±22° full lock, 12 Hz low-pass, recalibrate on countdown and on unpause;
   - Buttons: 120 ms ramp.
   Show a faint thumb ring where the steer gesture started.
G5. Buttons: press scale 0.94, 40 ms haptic, a hit area 1.25× the visual, and the drift button shows tier colour fill and pulses when the boost is ready. ITEM swipe-down threshold is 24 dp, hold-for-shield is 220 ms.
G6. The controls editor (v3) also gets presets "Compact", "Classic" and "Left-handed", and a live test track loop.
G7. Gamepad: add vibration on hits if the controller supports it, and use the same mapping as v3.

====================================================================
PART H — PRODUCTION READINESS
====================================================================
H1. Frame pacing: run the 60 Hz simulation on a dedicated thread with an accumulator (System.nanoTime), not a coroutine `delay` loop. The renderer interpolates between the last two sim states. GLSurfaceView renders continuously at the display rate, capped by the FPS setting.
H2. Thermal and battery: on API 29+ read `PowerManager.currentThermalStatus`. At MODERATE and above, step quality down one level and show a small "Cooling down" toast once. Turn on a battery saver mode at 15% battery when not charging (30 fps, no post FX).
H3. Stability: wrap GL resource creation and asset decoding in try/catch with fallbacks, catch OOM on texture load and retry at half resolution, write a local crash log (last 200 log lines + stack) to app files, and offer "Send report" (share intent) on next launch. There is no network upload.
H4. Memory: unload track textures and models when leaving a race. Keep ≤ 350 MB PSS on a 4 GB phone.
H5. App size: assets are already compressed; enable `isShrinkResources` and `isMinifyEnabled` for release ONLY if you add keep rules for any reflection (there is none in this codebase). Don't touch signing.
H6. Strings: move all user-facing text to `res/values/strings.xml` (prepare for Hindi). No hard-coded strings in composables.
H7. Store and legal: an About screen with CREDITS.txt (required for CC-BY), an in-app privacy notice ("No personal data leaves your phone; LAN play stays on your local network"), and a version label from BuildConfig.
H8. Back navigation: every screen handles back correctly. In a race, back opens pause. In multiplayer, leaving asks for confirmation and sends a clean LEAVE.
H9. Lifecycle: pausing the app during solo play pauses the race. In multiplayer it shows "Away" to others, and returning within 30 s resumes.

====================================================================
PART I — AUDIO POLISH
====================================================================
I1. Mix: music −14 LUFS-ish (volume 0.55), engine 0.45, SFX 0.8, UI 0.6. Duck music −6 dB under jingles and countdown, and −3 dB while boosting (the whoosh should cut through).
I2. Remote engines: the 3 nearest karts, pitched by their speed, panned by angle and attenuated by distance.
I3. No clicks: 30 ms fades on all loop starts/stops, and pause and resume all audio with the lifecycle.

====================================================================
PART J — TESTS & ACCEPTANCE
====================================================================
JVM tests that must pass: B14 network harness; TrackValidator + HeadlessRaceTest (8 tracks); HUD overlap and coverage tests (v3 E6 + F2); garage framing test (E2); lap/position unit tests (C1–C3); protocol encode/decode round-trips for every v4 message, including raceId/seq rejection.
Manual on 2 phones over a hotspot:
 [ ] Both phones get items from boxes; both can hit each other; hits spin out on the victim's screen.
 [ ] Positions on both screens match; no flicker; final results identical.
 [ ] Countdown in sync; same speed class; rematch works twice in a row; leave and rejoin works.
 [ ] Garage: kart fully visible and centred in the stage on every screen size.
 [ ] HUD at 90% is compact and readable; the road is visible.
 [ ] Textures are upright and colours are rich (compare with refs/).
 [ ] 30-minute session without a crash; no stutter when items fly.

====================================================================
PART K — DELIVERY ORDER (compile after each; say "continue" if you stop)
====================================================================
1. A4 conventions in the renderer (UV origin, sRGB/linear) — quick visual win.
2. Multiplayer root-cause fixes B1–B13 + the B14 harness.
3. Race logic polish C1–C7.
4. HUD rework F1–F6 + controls G1–G7.
5. Garage rebuild E1–E6.
6. Visual pack integration D2–D10.
7. Production readiness H1–H9 + audio I1–I3.
8. Full test pass (J).
