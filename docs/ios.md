# iOS app

Native SwiftUI app (iOS 17+) in [`ios/`](../ios), talking to the same backend as the Android app. Same design language, same rules (the server decides who sees whom, every reward and every fight).

## Layout

| Part | What |
| --- | --- |
| `ios/LocanaKit` | Swift package, no UI: wire models, the API client (token refresh once on a 401, single-flight), and the logic shared in spirit with Android: formatting, freshness, timeline titles, route replay maths, off-screen edge marks, crash and driving detectors, tracking policy. Tested with `swift test`. |
| `ios/Locana` | The app: SwiftUI screens, CoreLocation tracking and upload queue, CoreMotion activity and crash detection, a WebSocket client, local notifications, Keychain token store. |
| `ios/project.yml` | XcodeGen spec. The `.xcodeproj` and `Info.plist` are generated and not committed. |

## Build

```bash
brew install xcodegen
cd ios
xcodegen generate
open Locana.xcodeproj
```

Pick a simulator and run. The API address comes from the build setting `LOCANA_API_URL` (default `http://localhost:3000/`, which only works on the simulator):

```bash
xcodebuild build -project Locana.xcodeproj -scheme Locana -destination 'generic/platform=iOS Simulator' \
  LOCANA_API_URL=https://api.example.com/
```

Release builds should use an https address. Local plain HTTP is allowed only for local-network addresses.

## CI

`.github/workflows/ios.yml` runs on every push that touches `ios/` (on `macos-15`; macOS runners are free for public repositories, billed at 10x for private ones): package tests, app build, app tests on an iPhone 16 simulator, a launch check (the app must still be running after 10 s), and it keeps the simulator build (`Locana-simulator.zip`) and a first-launch screenshot as an artifact. It is separate from `release.yml`, so a macOS runner problem never blocks an Android release.

## Putting it on a phone (not done by CI)

This needs an Apple Developer account (paid) and signing: a bundle id (`app.locana.ios` unless you change it in `project.yml`), a distribution certificate and provisioning profile, then archive and upload to App Store Connect / TestFlight. Nothing in the repository holds Apple credentials. Capabilities to enable on the App ID: Background Modes (location). Push notifications (APNs) are not set up; see below.

## What is there, and what is not yet

Ported from Android: sign in and registration, circles (create, join with a code, invite), the map with members, focus and follow, a drawer, off-screen people at the screen edge, timeline with stays and trips named from-to, full-screen trip replay with a trackbar, driving events on trips, SOS (hold 1.5 s), emergency contacts, crash detection with a 15 s countdown, quick messages with falling emoji, landmarks that start locked and need a claim, quests with claim, level and achievements, collection with equip, monsters with weapons and the coin shop, notifications you can tap, privacy switches.

Not ported yet: the Mapbox map option, nearby-place chips and place popups, tile and territory overlays, leaderboards and the level path, quest history, saved places editing, block and report, profile photo upload, password reset and change, a light/dark override (the app follows the system), circle roles and challenges, the activity feed, cached member positions when offline.

Differences that come from iOS itself:

- No Firebase/APNs: events arrive over the WebSocket while the app is running (the location background mode keeps it running while tracking) and are shown as local notifications. A phone where iOS has fully stopped Locana learns about them when the app next opens.
- "Phone use while driving" counts the app being on screen, since iOS does not tell an app whether the screen is on while it is in the background.
- SOS opens the dialer with the first emergency contact filled in; iOS asks the person to confirm the call, an app cannot dial by itself.
- Location: iOS shows its own indicator while the app tracks in the background, and the person can choose "While using the app" only; tracking then continues only while the app is in use or recently used.

Verified here: package tests and the app build and tests on a macOS CI runner. Not verified: behaviour on a real iPhone (background location, motion sensors, notifications), because no device was available.
