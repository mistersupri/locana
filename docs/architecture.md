# Architecture

## Components

```
Android app                                   Backend (NestJS modular monolith)
┌───────────────────────────────┐             ┌──────────────────────────────────────────┐
│ ui/ (Compose screens, VMs)    │  HTTPS/REST │ auth      sessions, tokens, reset, Google │
│ domain/ (pure Kotlin rules)   │────────────▶│ users     profile, privacy, export, delete│
│ data/                         │             │ circles   membership, roles, invitations, │
│  api      Retrofit            │   WSS /ws   │           feed, CircleAccessService       │
│  realtime OkHttp WebSocket    │◀───────────▶│ location  ingestion, sharing sessions,    │
│  db       Room queue + cache  │             │           history, retention              │
│  location FGS, WorkManager    │             │ places    saved places, geofence engine   │
│  auth     Keystore tokens     │             │ game      XP ledger, levels, quests,      │
└───────────────────────────────┘             │           achievements, tiles, landmarks, │
                                              │           territories, leaderboards       │
                                              │ realtime  gateway + bus (Redis pub/sub)   │
                                              │ notifications, admin, health              │
                                              └───────────────┬───────────────┬──────────┘
                                                              │               │
                                                       PostgreSQL 16      Redis 7 (optional)
```

The PRD's logical components map to modules: presentation, domain and data layers on Android; identity (`auth`, `users`), location ingestion and validation (`location`), realtime distribution (`realtime`), circles (`circles`), game progression (`game`), notifications (`notifications`), persistence (Prisma), operations (`admin`, `health`).

## Decisions

| Decision | Reason |
| --- | --- |
| Modular monolith, not microservices | One deployable, one database transaction for reward grants. Modules talk through injected services, so a module can be split out later. |
| `CircleAccessService` is the only place that decides location visibility | Every REST read, every WebSocket event and every geofence alert calls it, so the rule cannot drift between features. |
| Recipients are computed when an event is published | A member removed a second ago never receives the next update. Sockets also must subscribe per circle; the subscription is checked against membership. |
| Redis is optional | Without `REDIS_URL` the rate limiter and event bus run in-process (single instance). With Redis they are shared by every instance. |
| XP ledger with `UNIQUE(user, source, sourceRef)` and `INSERT ... ON CONFLICT DO NOTHING` | Retries, double taps and concurrent requests can never grant the same reward twice; the profile total is only incremented when the ledger row was new. |
| Quest progress keyed by distinct ids (tiles, landmarks, days, sample refs) | Repeated GPS samples and replayed uploads add nothing. |
| Quest claims need an `Idempotency-Key` | A retried claim returns the original result; row locks serialize concurrent claims. |
| Territory rows locked with `SELECT ... FOR UPDATE` during a visit | Parallel visits produce exact scores and a single ownership change (covered by an e2e test). |
| Effects (notifications, feed, realtime) are published after the transaction commits | A rolled-back reward never produces a notification. |
| Slippy-map tiles at zoom 17 for exploration | Simple, documented, identical on server (`geo.ts`) and app (`Geo.kt`); a cross-platform test pins the ids. About 300 m wide near the equator. |
| No PostGIS | Landmarks and places are few per query; a bounding-box filter on indexed lat/lon plus haversine is enough. Revisit if landmarks grow past ~100k. |
| Exploration is a separate opt-in from sharing | A player can play solo without anyone seeing them; sharing can be on without the game using their location. |
| Pure Kotlin `domain` module | Tracking policy, tile math, backoff and validation are testable on the JVM in milliseconds. |

## Location pipeline

1. The app asks `GET /v1/sharing`. It collects location only when the answer has `collectLocation: true` (sharing active in some circle and not paused, or exploration on).
2. `TrackingService` (foreground service, type `location`) requests fixes from the Fused Location Provider using `TrackingPolicy`: 10 s while moving with the app open, 30 s in the background, 2 min when stationary, doubled on battery saver, 5 min when the battery is critical.
3. Fixes go into a Room queue with a per-install sequence number. They upload immediately (foreground, moving) or in batches; offline they wait and WorkManager retries with backoff when the network returns.
4. The server validates each sample (coordinates, accuracy, clock skew, staleness), flags risk signals (mock provider, impossible speed), stores only the newest position (`LatestLocation`, conditional on timestamp), appends optional history, runs geofences and pushes the new position to authorized viewers.
5. Trusted samples (accuracy 50 m or better, plausible speed, not mock) feed the game: tiles, landmarks, walking distance, quests, achievements, territories.
6. If the server answers `409 LOCATION_NOT_NEEDED`, the app clears its queue and stops the service.

## Notifications

- In the app: WebSocket events (`notification`, `level.up`, `achievement.unlocked`, ...) update screens and badges live.
- App closed: `NotificationSyncWorker` runs about every 15 minutes (Android's minimum for periodic work, and Doze can delay it further) and shows unread server notifications as system notifications. Arrival alerts can therefore be late when the app is closed.
- Locally generated: the tracking service shows its own ongoing notification; boot shows "sharing is waiting" if it cannot resume.
- Not built: true push while the app is closed. Options that avoid Firebase as a backend dependency: UnifiedPush with a self-hosted distributor (ntfy), or a persistent connection held by the foreground service while sharing is active. Both are documented trade-offs, not implemented.

## Android background behaviour

- Without "Allow all the time", tracking runs only while the app is visible; members then see the person as stale after 10 minutes. The UI says this instead of pretending.
- Android 12+ blocks starting a foreground service from the background; the app starts it from user actions in the foreground, or after boot only when background location is granted.
- Doze, App Standby buckets and OEM battery managers (Xiaomi, Oppo, Vivo, Huawei, Samsung) delay or kill background work. The Sharing screen links to battery settings. No technique here bypasses user controls or privacy indicators.
