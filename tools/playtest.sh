#!/bin/bash
# Emulator playtest: install, autoplay a race, record video + telemetry.
set -x
PKG=com.aistudio.deskracerlan.krtxzp
OUT=/tmp/playtest; mkdir -p $OUT
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r -g app/build/outputs/apk/debug/*.apk
adb logcat -c
for TRACK in ${PLAYTEST_TRACKS:-0}; do
  adb shell am force-stop $PKG
  adb shell am start -n $PKG/com.example.MainActivity --ez dr_autoplay true --ei dr_track $TRACK
  sleep 14
  adb exec-out screencap -p > $OUT/t${TRACK}_start.png
  adb shell screenrecord --time-limit 24 --bit-rate 8000000 /sdcard/t${TRACK}.mp4
  adb pull /sdcard/t${TRACK}.mp4 $OUT/t${TRACK}.mp4
  adb exec-out screencap -p > $OUT/t${TRACK}_end.png
done
adb logcat -d > $OUT/logcat_full.txt
grep -E "DRPERF|AndroidRuntime|FATAL|GLES|glError|Shader|DrmModel|E/" $OUT/logcat_full.txt | grep -v "^$" | tail -400 > $OUT/logcat.txt
true
