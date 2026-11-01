# Locana

[![CI](https://github.com/mistersupri/locana/actions/workflows/ci.yml/badge.svg)](https://github.com/mistersupri/locana/actions/workflows/ci.yml)

Social GPS tracker and location-based game for Android. People share their live location with circles they trust (a partner, family, friends) and turn everyday walks into a game: reveal map tiles, visit public landmarks, finish quests, earn XP and compete in friendly territory rounds with their circle.

| Part | Stack | Folder |
| --- | --- | --- |
| Android app | Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, Retrofit, OkHttp WebSocket, Google Maps SDK, Fused Location, WorkManager | [`android/`](android) |
| Backend | Node.js 22, NestJS 10, TypeScript, PostgreSQL 16 (Prisma), Redis 7 (optional), WebSocket gateway, OpenAPI | [`backend/`](backend) |

No Firebase, Supabase or other backend-as-a-service. The backend is the only authority for identity, circle membership, who may see whose location, XP, quests, achievements and rankings.

## Documentation

- [Architecture](docs/architecture.md): components, data flow, decisions
- [API and realtime contracts](docs/api.md): REST endpoints, WebSocket events, errors, idempotency, reconnect rules
- [Backend guide](docs/backend.md): local setup, environment variables, tests, deployment, migrations, backups, troubleshooting
- [Android guide](docs/android.md): build setup, Google Maps and Sign-In keys, signing and release, background location limits, battery test
- [Privacy and safety](docs/privacy.md): data categories, access rules, retention, deletion, UU PDP and Google Play notes
- [Design notes](docs/design.md): visual direction and the reason behind each design decision
- [CI and releases](docs/release.md): what runs on every push, how to cut a release, which secrets to set

## Quick start

```bash
cd backend && cp .env.example .env
```

```bash
docker compose up --build
```

```bash
docker compose run --rm api node dist/scripts/seed.js
```

Then open `android/` in Android Studio, add `MAPS_API_KEY` to `android/local.properties`, and run the `debug` build on an emulator (it talks to `http://10.0.2.2:3000/`). Details in the guides above.

## Status

What exists and what was verified in this repository, as of this commit.

### Backend: implemented and tested

- [x] Registration, login (email or username), Argon2id hashing, 15-minute JWT access tokens, rotating refresh tokens with reuse detection, logout, sign out other devices, device and session list
- [x] Password reset by e-mailed code (SMTP; logged instead of sent in development only), password change
- [x] Google Sign-In: server-side ID token verification (needs `GOOGLE_CLIENT_ID`)
- [x] Profiles, unique usernames, initials avatars with color choice, data export, account deletion
- [x] Circles: create, rename, delete, roles (owner/admin/member), ownership transfer, remove member, leave, mute
- [x] Invitations: 10-character random codes stored as hashes, expiry, use limits, revoke, rate-limited joining
- [x] Opt-in sharing sessions per circle with duration, pause, resume, stop, global pause, scheduled expiry
- [x] Location ingestion with validation, stale-update protection, probabilistic anti-cheat signals, optional history with retention
- [x] Authorization core: coordinates reach only members allowed at that moment (membership, active session, no global pause, not hidden, not blocked)
- [x] WebSocket gateway: header or first-message auth, per-circle subscriptions checked against membership, per-recipient delivery, Redis fan-out for multiple instances
- [x] Saved places and geofences with accuracy gating, hysteresis, confirmation and duplicate suppression; alerts only to members who can see the person
- [x] Game: configurable level curve, append-only XP ledger, quest engine (6 reusable conditions), idempotent claims, achievements, collectibles and cosmetics, exploration tiles, landmarks, territories with weekly rounds and decay, circle challenges, leaderboards with opt-out and aliases, weekly snapshots
- [x] Activity feed with reactions, notifications, block and report, admin endpoints (audit log, reports, risk signals, landmark curation)
- [x] Health and readiness checks, structured errors, rate limiting, Swagger UI in non-production, Dockerfile and Compose file
- [x] 38 unit tests and 34 end-to-end tests against PostgreSQL, all passing (the PRD's 12-step scenario, negative authorization cases, concurrent claims, territory concurrency)

### Android: built and tested on the JVM

- [x] Every screen on the PRD list, wired to the real API, with loading, empty, error and offline states. A few are sheets, dialogs or sections rather than full screens: member detail (map sheet), quest completion (dialog), active quest progress (quest detail), activity feed (Circle tab), splash (system splash API)
- [x] Foreground tracking service, offline upload queue (Room), WorkManager retries, boot handling, notification sync, Keystore-encrypted refresh token, WebSocket client with backoff and re-subscription
- [x] Debug and release (R8) builds succeed
- [x] 39 JVM tests pass: 18 domain tests (tile grid shared with the backend, tracking policy, backoff, freshness, validation), 20 Robolectric tests (offline queue, Room, WebSocket reconnect, ViewModel, Compose UI) and a full-app click-through against a running backend with a screenshot of every screen. That run found and fixed four bugs (see the commit history)
- [ ] Not run on a device or emulator yet: Google Map rendering (needs a Maps key), background tracking, battery use, OEM restrictions, and the minified release build at runtime

### Not built, or limited on purpose

- Remote push while the app is closed: no FCM (the PRD rules out Firebase as a dependency). A WorkManager job fetches notifications about every 15 minutes and Android may delay it. See [docs/architecture.md](docs/architecture.md#notifications).
- In-app chat (optional in the PRD) is not implemented.
- Landmarks: the seed has 10 well-known public places in central Jakarta with approximate coordinates, for development. Verify them and add your own city through `POST /v1/admin/landmarks` before launch.
- Logo and launcher icon are placeholders (the word "Locana" and a plain letter L). Replace before release.
- PostGIS is not used; bounding-box queries on indexed lat/lon are enough at this scale (see architecture notes).
- No legal review has been done. Nothing here claims compliance with UU PDP, GDPR or Google Play policy; see [docs/privacy.md](docs/privacy.md) for the open items.
