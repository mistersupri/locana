# Continuous integration and releases

Two GitHub Actions workflows live in `.github/workflows/`.

## CI (`ci.yml`)

Runs on every push to any branch and on every pull request. Four jobs run in parallel:

| Job | What it does |
| --- | --- |
| Backend tests | Type check, unit tests, end-to-end tests against PostgreSQL 16, production build |
| Android tests and debug build | Domain tests, Robolectric and Compose UI tests, debug APK (kept as an artifact for 14 days) |
| Full-app click-through | Starts the real backend (PostgreSQL, Redis, migrations, seed), then drives the real app through every main screen with `AppFlowTest`. Screenshots are uploaded as the `app-screens` artifact |
| Backend image builds | Builds the Docker image without pushing, so a broken Dockerfile fails the pull request |

## Release (`release.yml`)

Runs when you push a tag that starts with `v`:

```bash
git tag v0.2.0
```

```bash
git push origin v0.2.0
```

It runs the whole CI workflow first; nothing is released if any CI job fails. Then:

1. **Android**: builds the release APK and App Bundle. `versionName` comes from the tag (`0.2.0`), `versionCode` from the workflow run number, so every release is higher than the last.
2. **Backend image**: pushes `ghcr.io/<owner>/locana-backend` tagged `0.2.0`, `0.2` and `latest`.
3. **GitHub Release**: attaches the APK, the AAB and `SHA256SUMS.txt`, with notes generated from the commits.

Tags with a suffix, such as `v0.3.0-rc.1`, become pre-releases and do not move the `latest` image tag.

## Secrets and variables to set

In the repository: Settings, Secrets and variables, Actions.

| Name | Kind | Needed for |
| --- | --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Secret | Signing with your release key (base64 of the `.jks` file) |
| `ANDROID_KEYSTORE_PASSWORD` | Secret | Keystore password |
| `ANDROID_KEY_ALIAS` | Secret | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Secret | Key password |
| `MAPS_API_KEY` | Secret | Google Maps in release builds (restrict it to `app.locana` and your signing SHA-1) |
| `GOOGLE_SERVER_CLIENT_ID` | Secret | Optional: "Continue with Google" |
| `LOCANA_API_URL_RELEASE` | Variable | Your production API, e.g. `https://api.example.com/` (must be https) |

Without the four `ANDROID_*` secrets the release still runs, but the APK is signed with a throwaway debug key and named `locana-<version>-debug-signed.apk`. It installs for testing; Google Play will not accept it, so the App Bundle is skipped. The release notes say so.

Without `LOCANA_API_URL_RELEASE` the app points at `https://api.locana.invalid/` and cannot sign in. The workflow prints a warning.

### Create the release keystore (once)

Keep the keystore and its passwords somewhere safe and outside the repository. Losing it means you cannot update the app on Play unless you use Play App Signing with an upload key.

```bash
keytool -genkeypair -v -keystore locana-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Encode it for the secret (Linux; on macOS use `base64 -i locana-upload.jks`):

```bash
base64 -w0 locana-upload.jks
```

Paste the output into `ANDROID_KEYSTORE_BASE64`, and the alias and passwords into the other three secrets.

## Backend image

The image runs as the `node` user, uses `tini` for clean shutdowns and has a health check on `/health/ready`. It contains the Prisma CLI, so migrations and the seed run from the same image:

```bash
docker run --rm --env-file .env ghcr.io/<owner>/locana-backend:0.2.0 npx prisma migrate deploy
```

```bash
docker run --rm --env-file .env ghcr.io/<owner>/locana-backend:0.2.0 node dist/scripts/seed.js
```

New packages on GitHub Container Registry start private. To let servers pull without logging in, open the package page under your profile or organization, Package settings, and change the visibility.

## What was verified before this was committed

The workflows could not be run from the development environment. What was checked there:

- Both workflow files parse, every `needs` points at an existing job, and every `outputs` reference exists.
- The exact Gradle commands: release APK and AAB signed from environment variables (certificate and version checked with `apksigner` and `aapt2`), the debug-signed fallback, and the unsigned default.
- The Dockerfile steps without Docker: install, Prisma generate, build, production prune, then migrations and seed from the pruned output against PostgreSQL.
- The full-app click-through and every test suite the CI runs.

The first real run on GitHub is the remaining check. If a job fails there, its logs and the uploaded reports or screenshots show why.
