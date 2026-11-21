# lumix-lux-android

Capacitor 8.6 app v2.0 (`com.cameron.lumix`, Android 10+) that copies photos and videos off a Panasonic Lumix over Wi-Fi,
talking to the camera directly (no PC needed); works on the camera's own hotspot (`192.168.54.1`) or on home Wi-Fi. Saves
into the gallery (default `DCIM/Lumix`). Today it supports the ZS100 only; the multi-camera redesign plan is
`core/docs/REDESIGN.md` (read it before changing camera code).

- **Shared code is in `core/`** (git submodule, repo `lumix-lux-core`): the page `core/ui/index.html`, drivers, the protocol
  doc `core/docs/protocols/panasonic-camcgi.md`, `core/tools/fakecam.py`. `build.sh` copies the page into `www/` (gitignored
  there). Edit the page in `core/`, commit and push in `core/`, then commit the submodule bump here.
  If `core/` is empty: `git submodule update --init`.
- **Camera:** joins the home Wi-Fi "Via Network" (Wi-Fi → Remote Shooting & View) and is at `10.0.0.220`. It only answers
  while on that waiting screen, and serves one app at a time (others get 503 / `err_already_connected`), so stop the
  PC web app (lumix-lux-windows) or the phone app before testing.

## App

- **UI/logic:** `core/ui/index.html` (no bundler; `window.Capacitor`). Gallery grouped by day (dates from `X-REC_DATE_TIME`),
  filters/search/sort, select (long-press, drag, day, all), pull-to-refresh, fast scroll, viewer (pinch/double-tap zoom,
  Full size, Details/EXIF, Share, Open in), Remote screen (live view, shutter, zoom, focus, video), full-screen Settings.
  Android Back arrives as a native `back` event (`handleBack`).
- **Native** (`android/app/src/main/java/com/cameron/lumix/`): `LumixNativePlugin` (all camera HTTP pinned to the Wi-Fi network),
  `SaveQueue` (static download engine) + `SaveService`, `LocationService` + `LocationLog`.
  - Plugin methods: `httpGet/httpPost, bindWifi, listSaved, thumbs, camThumb, camDates/stopDates, exif, exifRemote, share, openExternal,
    exitApp, enqueue/queueState/cancelSave, freeSpace, requestNotifications, appInfo, setSystemBars, discover, cameraInfo,
    getCameraWifi/setCameraWifi/joinCameraWifi/leaveCameraWifi/joinState, locationStatus/setLocationRecording/clearLocationLog,
    startLiveView/stopLiveView`. Events: `queue, saved, date, back, frame, liveview, location`.
  - `SaveService` (foreground, `dataSync`): background saving with progress/Cancel notification; resume with `Range` (SharedPreferences
    `resume`), 3 tries per file, waits up to 60 s for the camera, wake + Wi-Fi lock, keepalive `getstate` every 5 s, GPS-tags JPEGs.
  - `LocationService` (foreground, `location`): fix every 2 min / 100 m into `filesDir/locations.csv`; photos within 10 min get GPS EXIF.
  - Permissions: INTERNET, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE, CHANGE_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE (SSDP),
    FOREGROUND_SERVICE(+_DATA_SYNC, +_LOCATION), POST_NOTIFICATIONS, WAKE_LOCK, VIBRATE, ACCESS_FINE/COARSE_LOCATION.
  - Settings (localStorage unless noted): camera address override + Find camera, auto-join camera hotspot (SSID prefix + password in
    SharedPreferences `camwifi`), gallery folder (every folder ever used is passed to `listSaved`), downloads at once 1/2, auto-save on
    connect, record location, theme System/Light/Dark. The app finds the camera by itself (`discover()`: multicast SSDP, then a /24 sweep).
  - The camera drops back to `rec` mode by itself some seconds after its last client leaves. Everything that lists or downloads
    sends `camcmd&value=playmode` first when `getstate` says otherwise, except while the Remote screen is open. Remote always ends
    with `stopstream` + `playmode`.
  - No clock-set command exists on the ZS100 (not offered); no deleting from the camera, ever.
- **Build:** `sh build.sh` → `dist/lumix.apk`. Bump `versionCode` in `android/app/build.gradle` per release (v2.0 = code 12).
- **Signing key:** `keystore/` (gitignored; copy it over from the old `lumix` project's `mobile/keystore/`). Losing it means updates
  won't install over the old app (uninstall first). Toolchain (Node 22, JDK 21, SDK 36) is in the Dockerfile; Gradle cache in volume
  `dc-lumix-gradle`.

## Testing (emulator and phone)

Wireless ADB to the user's phone, driven by `tools/phone.sh` (run in the container: `sh tools/phone.sh`; checklist in
`tools/TESTING.md`). The release build has WebView debugging on. Always use explicit `ip:port` (no mDNS through Docker NAT).
Never uninstall the app (it would orphan the saved photos); `install` uses `-r`.

- **Once:** phone Developer options > Wireless debugging > "Pair device with pairing code", then `phone.sh pair <ip:port> <code>`.
  Then `phone.sh connect <ip:port>` (the port on the main Wireless debugging screen; it changes whenever debugging is toggled).
  The target is remembered in `tools/.device`.
- **Common:** `status`, `install`, `launch`, `stop`, `shot` (prints a PNG path to view), `logs [n]`, `eval '<js>'` / `console [secs]`
  (Chrome DevTools Protocol via `cdp.mjs`, page globals like `cam`, `sync` work), `tap x y`, `text '...'`, `back`.
- Test on the emulator by default; a few test saves there are fine (newest files, never the whole card). Ask the user before
  saving to their phone or taking test shots.

**Emulator (default for development):** AVD `lumix_s23plus` (Android 13 / API 33, 1080x2340, density 400, Google APIs + Play Store,
x86_64; stock Google Android). The older `lumix_pixel5` (Android 12 / API 31) still exists; run only one at a time (both use adb port 5555).
It runs on the Windows host; the container reaches it at `192.168.65.254:5555`. Start it from PowerShell:
`& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd lumix_s23plus -no-snapshot-save -gpu auto`. Then in the container
`sh tools/phone.sh emulator` (once per session) and prefix everything with `PHONE_TARGET=emulator`. It reaches the camera at
`10.0.0.220` through the host's NAT (home-Wi-Fi mode only; no hotspot; live view needs `adb emu redir add udp:49199:49199` +
`tools/udprelay.py`). Fake camera: `core/tools/fakecam.py`. `tools/out/` is scratch. Details and limits: `tools/TESTING.md`.

History: `docs/PLAN-v2.md` is the build log of v1.4 → v2.0 (paths in it are from the old single repo).
