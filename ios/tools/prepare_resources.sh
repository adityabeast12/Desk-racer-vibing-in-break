#!/usr/bin/env bash
# Assemble the iOS bundle resources from the Android sources (run from repo root).
set -euo pipefail
R=ios/iosApp/Resources
rm -rf "$R/assets" "$R/fonts"
mkdir -p "$R/assets" "$R/fonts"
# Android assets + install-time asset pack (same merge the APK build does); OGG is replaced by M4A
rsync -a --exclude '*.ogg' app/src/main/assets/ "$R/assets/"
rsync -a --exclude '*.ogg' asset-pack/ "$R/assets/"
rsync -a ios/audio-m4a/ "$R/assets/"
cp app/src/main/res/font/*.ttf "$R/fonts/"
cp brand/res_override/font/*.ttf "$R/fonts/" 2>/dev/null || true
echo "assets: $(find "$R/assets" -type f | wc -l) files, $(du -sh "$R/assets" | cut -f1); fonts: $(ls "$R/fonts" | wc -l)"
