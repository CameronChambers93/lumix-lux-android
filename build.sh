#!/bin/sh
# Build the signed Android app into dist/lumix.apk. Run inside the lumix-lux-android container.
# Bump versionCode in android/app/build.gradle for each release you install over an older one.
set -e
cd "$(dirname "$0")"
[ -f core/ui/index.html ] || { echo "core/ is empty: run git submodule update --init" >&2; exit 1; }
# The page, the camera runtime and the drivers are shared with the iOS app and live in lumix-lux-core.
cp core/ui/index.html www/index.html
rm -rf www/js www/drivers && mkdir -p www/js www/drivers
cp core/js/lux-camera.js www/js/
cp core/drivers/*.json www/drivers/
# The page loads the drivers listed here (the bundled set; downloaded ones come with Phase 6 of core/docs/REDESIGN.md).
node -e 'const fs = require("fs"), d = "www/drivers";
  fs.writeFileSync(d + "/index.json", JSON.stringify(fs.readdirSync(d).filter((f) => f.endsWith(".json")).sort()));'
# No bundler: the page loads Capacitor's runtime as a plain script.
cp node_modules/@capacitor/core/dist/capacitor.js www/
npx cap sync android
(cd android && ./gradlew --no-daemon -q assembleRelease)
mkdir -p dist
cp android/app/build/outputs/apk/release/app-release.apk dist/lumix.apk
echo "built dist/lumix.apk"
