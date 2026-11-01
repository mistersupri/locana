# Android guide

## Requirements and versions

| Item | Version | Why |
| --- | --- | --- |
| JDK | 17+ (21 works) | AGP 8.7 needs 17 |
| Gradle | 8.14.3 (wrapper) | |
| Android Gradle Plugin | 8.7.3 | Supports compileSdk 35 |
| Kotlin | 2.0.21 with the Compose compiler plugin | KSP 2.0.21-1.0.28 matches it |
| compileSdk / targetSdk | 35 | Highest platform installed where this was written. Google Play raises the minimum target level every year (usually each August); check the current requirement and move to 36 if needed before submitting |
| minSdk | 26 (Android 8.0) | Adaptive icons, `java.time`, notification channels without desugaring |
| Compose BOM | 2024.12.01 | Material 3 1.3 |
| Hilt | 2.53.1 | |
| Room | 2.6.1 | |
| Maps Compose | 6.4.1, Play Services Maps 19.0.0, Location 21.3.0 | |

All versions are pinned in `gradle/libs.versions.toml`.

## Build status

Verified in this repository: `:app:assembleDebug` and `:app:assembleRelease` (R8 minified, unsigned) build, and 39 JVM tests pass, including a full-app click-through against a running backend (below).

Not verified yet: running on a real device or emulator (no hardware acceleration where this was built), the Google Map itself (needs `MAPS_API_KEY`), the release build at runtime (R8 rules are in place for kotlinx.serialization and Retrofit, but a minified build has not been launched), background tracking and battery use.

## Setup

1. Open the `android/` folder in Android Studio (Ladybug or newer).
2. Create `android/local.properties` (not committed):

```
sdk.dir=/path/to/Android/sdk
MAPS_API_KEY=your-android-restricted-maps-key
GOOGLE_SERVER_CLIENT_ID=your-web-oauth-client-id.apps.googleusercontent.com
LOCANA_API_URL_DEBUG=http://10.0.2.2:3000/
LOCANA_API_URL_STAGING=https://staging-api.example.com/
LOCANA_API_URL_RELEASE=https://api.example.com/
```

3. Start the backend (see `docs/backend.md`), then run the `debug` build.

Without `MAPS_API_KEY` the app still runs: map areas show an explanation and the member list stays usable. Without `GOOGLE_SERVER_CLIENT_ID` the "Continue with Google" button is hidden.

Build types: `debug` (cleartext allowed only to the emulator host and localhost), `staging` and `release` (HTTPS and WSS only; the build fails if their API URL is not `https://`).

## Google Maps key

1. In Google Cloud Console create a project and enable **Maps SDK for Android**.
2. Create an API key. Under "Application restrictions" choose Android apps and add each package and SHA-1: `app.locana.debug` with your debug keystore SHA-1, `app.locana.staging`, and `app.locana` with your release (or Play App Signing) SHA-1.
3. Under "API restrictions" allow only Maps SDK for Android.
4. Billing: a billing account is required for the key to work. Check Google Maps Platform's current price list for Maps SDK for Android before launch. Geocoding, Places and routing APIs are not used, so they add no cost.

Get the debug SHA-1:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

## Google Sign-In (optional)

1. In the same Cloud project, configure the OAuth consent screen.
2. Create an OAuth client of type **Web application**. Put its client id in `GOOGLE_SERVER_CLIENT_ID` (app) and `GOOGLE_CLIENT_ID` (backend).
3. Create an OAuth client of type **Android** for each package and SHA-1 (no value to copy; it authorizes the app).

The app gets an ID token through Credential Manager and the server verifies it with Google before trusting the e-mail.

## Tests

```bash
./gradlew :domain:test
```

```bash
./gradlew :app:testDebugUnitTest
```

App unit tests run on the JVM with Robolectric: offline upload queue (MockWebServer and in-memory Room), Room DAOs, WebSocket reconnect and re-subscribe, AuthViewModel validation and sign-in, Compose UI components and route builders.

### Full-app click-through

`app/src/test/kotlin/app/locana/e2e/AppFlowTest.kt` launches the real `MainActivity` with the real Hilt graph (only the API URL and token cipher are replaced) and drives it against a running backend: registration with validation errors, profile setup, permission education with everything declined, circle creation, invitation code, granting location and sharing for one hour, quests, quest detail, landmark search (no distances without a fix), Play, achievements, collection, leaderboard, circle feed and members, sharing controls, privacy, dark theme, sign out and sign in again. It saves a screenshot of each screen.

It is skipped unless `LOCANA_E2E_API` is set. With the backend running locally (see `docs/backend.md`):

```bash
LOCANA_E2E_API=http://127.0.0.1:3000/ LOCANA_SHOTS_DIR=/tmp/locana-shots ./gradlew :app:testDebugUnitTest --tests 'app.locana.e2e.AppFlowTest'
```

Screenshots draw the activity window, so bottom sheets and dialogs (separate windows) do not appear in them, although the test interacts with them.

### Build troubleshooting

- `Unable to find method metafactory` while compiling unit-test Java (Hilt test components): the SDK platform's `core-for-system-modules.jar` is incomplete. Reinstall "Android SDK Platform 35" with the SDK Manager. A stripped copy without `java/lang/invoke/LambdaMetafactory` was the cause where this was built.
- HTTP 429 from Maven Central during dependency download: retry, or resolve through a mirror with a local Gradle init script (do not commit mirror settings).

## Signing and release

Releases are built by GitHub Actions when you push a `v*` tag; see [release.md](release.md) for the secrets it needs. The steps below are for building a signed release on your own machine.

1. Create an upload keystore once and keep it out of git:

```bash
keytool -genkeypair -v -keystore locana-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

2. Create `android/keystore.properties` (ignored by git):

```
storeFile=../locana-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

3. Build an App Bundle:

```bash
./gradlew :app:bundleRelease
```

4. Enroll in Play App Signing and upload `app/build/outputs/bundle/release/app-release.aab`.

Release builds are minified with R8, contain no debug logging of request bodies, have backups disabled, and refuse non-HTTPS URLs. Before publishing, replace the placeholder launcher icon and splash mark (a plain letter L) with the final logo.

### Google Play declarations

- Background location needs the Location permissions declaration in Play Console, with a video showing the in-app explanation and the "Allow all the time" request. The in-app reason: keep sharing with chosen circles while the app is closed.
- Foreground service type `location` needs the Foreground service declaration.
- Data safety form: precise location (shared with chosen users, optional history), name, e-mail, app activity (game progress). No data sold, no advertising.
- Locana shows other people's locations. Play's User Data and location policies expect that people know when their location is collected and shared; the app keeps a persistent notification whenever it collects location, and sharing is always started explicitly per circle. Review the current policy text before submitting.

## Background location: what to expect

| Situation | Behaviour |
| --- | --- |
| App open, moving | Fix about every 10 s, uploaded right away |
| App closed, "Allow all the time" granted | Foreground service with a visible notification; fixes every 30 s when moving, 2 min when still |
| App closed, only "While using the app" | No updates. Members see the last position turn STALE after 10 minutes |
| Battery saver or below 20 % | Intervals double, balanced accuracy |
| Below 10 % and not charging | Low-power fixes every 5 min, uploads batched |
| Offline | Fixes queue in Room (up to 23 h), uploaded when the network returns |
| Doze, OEM battery managers | Updates may be delayed or stopped. Xiaomi, Oppo, Vivo, Huawei and some Samsung builds are aggressive; users may need to allow unrestricted battery use (linked from the Sharing screen) |
| Reboot | Resumes only if sharing was active and background location is granted; otherwise a notification asks the user to open the app |

Android 10+ shows a location indicator whenever location is used; the app never tries to hide it.

## Battery test scenario

Run on at least two physical devices (one recent Pixel, one mid-range device from an OEM with aggressive battery management), on the release build.

1. Charge to 100 %, enable sharing with one circle "until I stop", grant background location, close the app.
2. Walk 30 minutes, sit still 2 hours, commute 30 minutes, then leave the phone idle overnight.
3. Record battery level every hour and capture `adb shell dumpsys batterystats --charged app.locana` at the end; reset first with `adb shell dumpsys batterystats --reset`.
4. Repeat with sharing off (baseline) and with exploration on (HIGH accuracy while moving).
5. Compare drain against the baseline and note how many uploads reached the server (`LocationHistory` with history on).

Do not publish a battery percentage until these measurements exist.

## Profiling

- Android Studio Profiler (CPU, memory, energy) on the release build with `profileable` enabled.
- `adb shell dumpsys location` shows active location requests and their intervals.
- Layout Inspector with recomposition counts for the Map screen; markers are recomposed only when member data changes.
- `adb shell dumpsys jobscheduler | grep locana` to watch WorkManager jobs under Doze (`adb shell dumpsys deviceidle force-idle` to simulate).
