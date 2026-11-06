#!/usr/bin/env bash
# Boot an iPhone simulator, install the app, auto-play a race and capture screenshots + logs.
set -uo pipefail
APP="$1"; OUT="$2"; TRACK="${3:-0}"
BUNDLE=com.aistudio.deskracerlan.ios
mkdir -p "$OUT"
DEV=$(xcrun simctl list devices available -j | python3 -c '
import json,sys
d=json.load(sys.stdin)["devices"]
best=None
for rt,devs in d.items():
    if "iOS" not in rt: continue
    for x in devs:
        n=x["name"]
        if n.startswith("iPhone") and "Pro Max" not in n and "mini" not in n and "SE" not in n:
            best=x["udid"]
print(best or "")')
echo "simulator: $DEV"
[ -n "$DEV" ] || { echo "::error::no iPhone simulator"; exit 1; }
xcrun simctl boot "$DEV" || true
xcrun simctl bootstatus "$DEV" -b
xcrun simctl install "$DEV" "$APP" || { echo "::error::install failed"; exit 1; }

shot() { xcrun simctl io "$DEV" screenshot --type=png "$OUT/$1.png" >/dev/null 2>&1 || true; }

# 1) normal launch: splash -> main menu
xcrun simctl launch --stdout="$OUT/stdout_menu.txt" --stderr="$OUT/stderr_menu.txt" "$DEV" "$BUNDLE"
sleep 6; shot 01_menu
sleep 3; shot 02_menu_later
xcrun simctl terminate "$DEV" "$BUNDLE" || true
sleep 1

# 2) autoplay race (CPU drives the local kart) + short video
xcrun simctl launch --stdout="$OUT/stdout_race.txt" --stderr="$OUT/stderr_race.txt" "$DEV" "$BUNDLE" -dr_autoplay 1 -dr_track "$TRACK"
sleep 5; shot 03_grid
xcrun simctl io "$DEV" recordVideo --codec=h264 --force "$OUT/race.mp4" > /dev/null 2>&1 &
VPID=$!
for i in 1 2 3 4 5 6 7 8; do sleep 3; shot "$(printf '1%d_race' "$i")"; done
kill -INT $VPID 2>/dev/null || true
sleep 2
if xcrun simctl spawn "$DEV" launchctl list 2>/dev/null | grep -q "$BUNDLE"; then echo "app still running: OK"; else echo "::warning::app is not running at the end (crash?)"; fi
xcrun simctl terminate "$DEV" "$BUNDLE" || true
# crash logs, if any
find ~/Library/Logs/DiagnosticReports -name 'DeskRacer*' -newer "$OUT/01_menu.png" -exec cp {} "$OUT/" \; 2>/dev/null || true
ls -la "$OUT"
echo "--- stdout (race) tail"; tail -40 "$OUT/stdout_race.txt" 2>/dev/null || true
echo "--- stderr (race) tail"; tail -40 "$OUT/stderr_race.txt" 2>/dev/null || true
