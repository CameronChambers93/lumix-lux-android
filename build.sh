#!/bin/sh
# Build the signed Android app into dist/lumix.apk. Run inside the lumix-lux-android container.
# Bump versionCode in android/app/build.gradle for each release you install over an older one.
set -e
cd "$(dirname "$0")"
[ -f core/ui/index.html ] || { echo "core/ is empty: run git submodule update --init" >&2; exit 1; }
# The page is shared with the iOS app and lives in lumix-lux-core.
cp core/ui/index.html www/index.html
# No bundler: the page loads Capacitor's runtime as a plain script.
cp node_modules/@capacitor/core/dist/capacitor.js www/
npx cap sync android
(cd android && ./gradlew --no-daemon -q assembleRelease)
mkdir -p dist
cp android/app/build/outputs/apk/release/app-release.apk dist/lumix.apk
echo "built dist/lumix.apk"
