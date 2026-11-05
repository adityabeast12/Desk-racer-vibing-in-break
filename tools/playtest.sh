#!/bin/bash
# Emulator playtest: install, autoplay a race in deterministic capture mode (every frame = 1/30 s of
# game time, dumped as JPEG), plus a short real-time run for HUD screenshots + fps telemetry.
set -x
PKG=com.aistudio.deskracerlan.krtxzp
OUT=/tmp/playtest; mkdir -p $OUT
adb wait-for-device
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell wm size 1280x576
adb shell wm density 200
adb install -r -g app/build/outputs/apk/debug/*.apk
adb logcat -c
for TRACK in ${PLAYTEST_TRACKS:-0}; do
  adb shell am force-stop $PKG
  adb shell am start -n $PKG/com.example.MainActivity --ez dr_autoplay true --ez dr_capture true --ei dr_track $TRACK
  DIR=/sdcard/Android/data/$PKG/files/capture
  for i in $(seq 1 160); do
    sleep 5
    if adb shell "[ -f $DIR/done.txt ]"; then break; fi
    [ $((i % 6)) -eq 0 ] && adb shell "ls $DIR | wc -l"
  done
  mkdir -p $OUT/cap_t$TRACK
  adb pull $DIR/. $OUT/cap_t$TRACK/ > /dev/null
  adb exec-out screencap -p > $OUT/t${TRACK}_hud.png
done
# short real-time run (fps telemetry under software GL — absolute numbers are emulator-bound)
adb shell am force-stop $PKG
adb shell am start -n $PKG/com.example.MainActivity --ez dr_autoplay true --ei dr_track 0
sleep 30
adb exec-out screencap -p > $OUT/realtime_hud.png
adb logcat -d > $OUT/logcat_full.txt
grep -E "DRPERF|DRCAP|AndroidRuntime|FATAL|glError|Shader" $OUT/logcat_full.txt | tail -300 > $OUT/logcat.txt
true
