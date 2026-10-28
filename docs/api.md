# API and realtime contracts

Base URL: `https://<host>/v1` (health endpoints are at `/health` and `/health/ready`, without the prefix). In development the live OpenAPI document and Swagger UI are at `/docs`; Swagger is disabled when `NODE_ENV=production`.

All bodies are JSON. Timestamps are ISO-8601 UTC strings. Every route needs `Authorization: Bearer <access token>` unless marked public.

## Errors

Every error has the same shape:

```json
{ "error": { "code": "CIRCLE_NOT_FOUND", "message": "Circle not found", "details": null } }
```

| Status | Typical codes | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` (details lists fields), `INVALID_INVITATION`, `INVALID_RESET_CODE`, `INVALID_IDEMPOTENCY_KEY` | The request is wrong; retrying the same request will not help. Unknown fields are rejected. |
| 401 | `MISSING_TOKEN`, `INVALID_ACCESS_TOKEN`, `SESSION_REVOKED`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` | Refresh the access token once; if refresh fails, sign in again. |
| 403 | `ROLE_REQUIRED`, `WRONG_PASSWORD`, `ADMIN_ONLY` | Authenticated but not allowed. |
| 404 | `CIRCLE_NOT_FOUND`, `PLACE_NOT_FOUND`, ... | Also returned for resources that exist but you may not see, so ids cannot be probed. |
| 409 | `EMAIL_TAKEN`, `USERNAME_TAKEN`, `LOCATION_NOT_NEEDED`, `QUEST_NOT_COMPLETE`, `QUEST_EXPIRED` | State conflict. `LOCATION_NOT_NEEDED` tells the device to stop collecting location. |
| 422 | `IDEMPOTENCY_KEY_REUSED` | The key was already used for a different claim. |
| 429 | `RATE_LIMITED` | Back off. Limits are per user (or per IP on public routes). |
| 5xx | `INTERNAL` | Retry later with backoff. |

## Pagination

Feeds and notifications use cursors: the response has `nextCursor` (an ISO timestamp); pass it as `?cursor=` for the next page, `null` means the end. Leaderboards use `limit` (1-100, default 50) and `offset`.

## Authentication

| Method and path | Body | Response |
| --- | --- | --- |
| `POST /auth/register` (public) | `{email, password (10-128), username ([a-z0-9_.]{3,24}), displayName, deviceName?}` | `201 {tokens, user}` |
| `POST /auth/login` (public) | `{identifier (email or username), password, deviceName?}` | `{tokens, user}` |
| `POST /auth/google` (public) | `{idToken, deviceName?}` | `{tokens, user}`; `501 GOOGLE_SIGN_IN_DISABLED` without server config |
| `POST /auth/refresh` (public) | `{refreshToken}` | `{accessToken, accessTokenExpiresIn, refreshToken, sessionId}` |
| `POST /auth/password/forgot` (public) | `{email}` | `202` always (does not reveal accounts) |
| `POST /auth/password/reset` (public) | `{email, code, newPassword}` | `200`; signs out every device |
| `POST /auth/password/change` | `{currentPassword, newPassword}` | `200`; signs out other devices |
| `POST /auth/logout` | none | `204`, ends this login |
| `POST /auth/logout-others` | none | `204` |
| `GET /auth/sessions` | none | `[{id, deviceName, userAgent, createdAt, lastUsedAt, current}]` |
| `POST /auth/sessions/:id/revoke` | none | `204` |

Tokens: access tokens are HS256 JWTs valid 15 minutes. Refresh tokens are 256-bit random strings stored as SHA-256 hashes, valid 30 days, and rotate on every use. Presenting an already-rotated refresh token revokes that whole login (theft detection).

```json
// POST /v1/auth/login
{ "identifier": "rani", "password": "correct horse battery", "deviceName": "Pixel 8" }
// 200
{ "tokens": { "accessToken": "eyJ...", "accessTokenExpiresIn": 900, "refreshToken": "q3V...", "sessionId": "8f1c..." },
  "user": { "id": "4b2e...", "username": "rani", "profile": { "displayName": "Rani", "onboardingDone": true, "...": "..." },
            "progress": { "level": 3, "totalXp": 310, "nextLevelXp": 450, "levelProgress": 0.3, "...": "..." },
            "privacy": { "sharingPaused": false, "explorationEnabled": true, "...": "..." } } }
```

## Me

| Method and path | Notes |
| --- | --- |
| `GET /me` | Profile, progress, privacy |
| `PATCH /me` | `{displayName?, username?, avatarKey?, bio?, onboardingDone?, teamCircleId?}`; team circle must be one of yours |
| `PUT /me/cosmetics` | `{avatarFrameKey, titleKey, markerKey}` (null unequips); must own the item |
| `PATCH /me/privacy` | `{historyEnabled?, historyRetentionDays? (1-30), explorationEnabled?, showInLeaderboards?, leaderboardAlias?, shareExplorationWithCircles?, notifyArrivals?, notifyQuests?, notifyAchievements?, notifyCircle?}`. Turning history off deletes it. |
| `POST /me/devices` | `{installId, model?, appVersion?}` |
| `GET /me/export` | Everything stored about you, as JSON (5 per hour) |
| `POST /me/delete` | `{confirm: "DELETE", password?}` (password required for password accounts) |
| `GET /me/location-history?from&to` | Your own history only |
| `DELETE /me/location-history?before` | `{deleted}` |
| `GET /me/blocks`, `PUT /users/:id/block`, `DELETE /users/:id/block` | Blocking hides both people from each other everywhere |
| `POST /users/:id/report` | `{reason: UNWANTED_TRACKING, HARASSMENT, IMPERSONATION, SPAM or OTHER, details?, circleId?}`; only about circle-mates |
| `GET /notifications?cursor&limit`, `POST /notifications/read {ids?}` | Omit `ids` to mark all read |

## Circles

| Method and path | Who | Notes |
| --- | --- | --- |
| `GET /circles` | any | Your circles with `myRole`, `memberCount`, `iAmSharing` |
| `POST /circles` | any | `{name, avatarKey?}`; you become owner |
| `GET /circles/:id` | member | Members with `sharingWithMe`, `blockedByMe`; `hiddenFromViewerIds` lists who you hid from |
| `PATCH /circles/:id` | owner, admin | `{name?, avatarKey?}` |
| `DELETE /circles/:id` | owner | |
| `POST /circles/:id/leave` | member | Owner leaving hands over to the oldest admin, then the oldest member |
| `POST /circles/join` | any | `{code}` in any case, with or without dashes; 10 tries per hour |
| `GET/POST /circles/:id/invitations` | owner, admin | `POST {expiresInHours 1-168, maxUses 1-50}` returns the plain `code` and `deepLink` once |
| `DELETE /circles/:id/invitations/:invId` | owner, admin | revoke |
| `PUT /circles/:id/members/:userId/role` | owner | `{role: ADMIN, MEMBER or OWNER}`; OWNER transfers ownership |
| `DELETE /circles/:id/members/:userId` | owner, admin | admins may remove members only |
| `PATCH /circles/:id/me` | member | `{muted}` |
| `PUT /circles/:id/visibility` | member | `{viewerId, hidden}` hides your location from one member |
| `GET /circles/:id/feed?cursor` | member | Feed items; payloads never contain coordinates |
| `PUT /circles/feed/:itemId/reaction` | member | `{emoji: clap, fire, heart, wow, laugh or null}` |
| `GET/POST /circles/:id/challenges` | member | `POST {title, metric: TILES, LANDMARKS or QUESTS, target 1-500, days 1-30}` |
| `GET /circles/:id/exploration` | member | Aggregated totals of members who allow it |

## Location and sharing

| Method and path | Notes |
| --- | --- |
| `GET /sharing` | `{globallyPaused, explorationEnabled, collectLocation, sessions[]}` |
| `POST /sharing` | `{circleId, durationMinutes? (5-10080)}` starts or replaces the session for that circle |
| `POST /sharing/:id/pause`, `/resume`, `/stop` | per session |
| `PUT /sharing/pause-all` | `{paused}` |
| `POST /sharing/stop-all` | ends every session |
| `POST /location` | `{samples: [1-100]}`; 240 requests per 10 minutes |
| `GET /circles/:id/locations` | One entry per member, as the caller may see them |

Location sample:

```json
{ "latitude": -6.1754, "longitude": 106.8272, "accuracyM": 9.5, "recordedAt": "2026-10-28T09:41:12Z",
  "sequence": 1842, "speedMps": 1.3, "bearingDeg": 210, "altitudeM": 12, "source": "fused",
  "activity": "walking", "isMock": false, "batteryPct": 64 }
```

Upload response:

```json
{ "accepted": 3, "rejected": [{ "index": 0, "reason": "STALE_SEQUENCE" }], "latestRecordedAt": "2026-10-28T09:41:12Z",
  "sharingActive": true,
  "game": { "xpGained": 60, "newTiles": ["17/104430/67788"], "landmarks": [{ "id": "...", "name": "Monumen Nasional", "firstVisit": true }],
            "levelUps": [], "achievements": [], "questUpdates": [{ "userQuestId": "...", "questName": "Landmark check-in", "progress": 1, "target": 1, "completed": true }] } }
```

Rejection reasons: `INVALID_COORDINATE`, `INVALID_ACCURACY`, `FUTURE_TIMESTAMP` (more than 2 minutes ahead), `TOO_OLD` (over 24 hours), `STALE_SEQUENCE` (not newer than the stored position). `game` is `null` when exploration is off or no sample was trusted.

Member location entry:

```json
{ "userId": "...", "status": "LIVE", "location": { "latitude": -6.17, "longitude": 106.82, "accuracyM": 12, "recordedAt": "...", "batteryPct": 64 } }
{ "userId": "...", "status": "HIDDEN", "location": null }
```

`LIVE`: fix under 10 minutes old. `STALE`: older, still shared. `UNAVAILABLE`: sharing but no fix yet. `HIDDEN`: not shared with you; no coordinates and no timestamp.

## Places

| Method and path | Notes |
| --- | --- |
| `GET /circles/:id/places` | members only |
| `POST /circles/:id/places` | `{name, type: HOME, WORK, SCHOOL or CUSTOM, latitude, longitude, radiusM (75-2000), notifyArrive?, notifyLeave?, notifyUserIds?}` |
| `PATCH /places/:id`, `DELETE /places/:id` | creator, owner or admin |

Geofence rules: samples less accurate than max(100 m, radius) are ignored; "outside" requires distance beyond radius plus max(50 m, accuracy); a transition needs two agreeing samples 30 s apart (or one precise sample deep inside or outside); the same alert type is suppressed for 5 minutes and any alert within 90 s of the previous one. Alerts go only to members who can see the person at that moment.

## Game

| Method and path | Notes |
| --- | --- |
| `GET /quests` | Creates this period's quests if needed, returns active, completed and recently claimed |
| `GET /quests/history?cursor`, `GET /quests/:id` | |
| `POST /quests/:id/claim` | Header `Idempotency-Key` (8-64 url-safe chars) required |
| `GET /achievements`, `GET /collectibles`, `GET /levels` | |
| `GET /leaderboards?scope=friends,circle or global&metric=xp,tiles or quests&period=week or season&circleId&limit&offset` | |
| `GET /leaderboards/history?board=WEEKLY_XP&periodKey=2026-W44` | frozen weekly results |
| `GET /exploration/tiles?north&south&east&west` | your own tiles only |
| `GET /exploration/stats` | |
| `GET /landmarks?lat&lon&radiusM&q` | public places, nearest first |
| `GET /territories?north&south&east&west`, `GET /territories/:id` | owner circle names and weekly circle scores; never member names or exact visit times |
| `GET /events` | current and upcoming game events |

Claim idempotency: the same key returns the original result with `alreadyClaimed: true`; a new key for an already claimed quest also returns `alreadyClaimed: true`; XP is granted once in every case. Concurrent claims are serialized with a row lock.

Quest conditions (data, not code): `DISCOVER_TILES`, `VISIT_LANDMARKS` (optionally limited to `params.landmarkIds`), `WALK_DISTANCE` (meters at 0.3-4.5 m/s), `CIRCLE_TILES` (team circle), `ACTIVE_DAYS`, `COLLECT_LANDMARK_SET` (`params.setCode`).

## Admin

Limited to user ids in `ADMIN_USER_IDS`: `GET /admin/audit?action&limit`, `GET /admin/reports`, `PATCH /admin/reports/:id {status}`, `GET /admin/risk` (7-day risk signal totals), `POST /admin/landmarks`.

## WebSocket

Endpoint: `wss://<host>/ws`.

Authentication: send `Authorization: Bearer <access token>` on the upgrade request, or send `{"type":"auth","token":"..."}` as the first message within 5 seconds. Tokens are never accepted in the URL. After an access token refresh, send a new `auth` message on the open socket; sockets whose session ended (logout, password change, expiry without re-auth) are closed with code `4401`.

Client messages (at most 120 per minute):

```json
{ "type": "subscribe", "circleId": "..." }
{ "type": "unsubscribe", "circleId": "..." }
{ "type": "ping" }
```

Server messages all look like `{ "type": "...", "data": { ... }, "ts": "..." }`:

| Type | Data | Delivered to |
| --- | --- | --- |
| `ready` | `{userId}` | the socket, after auth |
| `subscribed` / `unsubscribed` | `{circleId}` | the socket |
| `error` | `{code}` (`UNAUTHENTICATED`, `CIRCLE_NOT_FOUND`, `RATE_LIMITED`, `BAD_JSON`, `UNKNOWN_TYPE`) | the socket |
| `location.update` | `{circleId, userId, latitude, longitude, accuracyM, speedMps, bearingDeg, recordedAt, batteryPct}` | members allowed to see that person in that circle, on sockets subscribed to that circle |
| `member.availability` | `{circleId, userId, status: SHARING or HIDDEN}` | circle members; HIDDEN means drop that person's coordinates now |
| `sharing.status` | same as `GET /sharing` | the owner of the sessions |
| `circle.membership` | `{circleId, userId, change: JOINED, LEFT, REMOVED, ROLE_CHANGED or DELETED}` | circle members, plus the removed person; removal ends their subscription |
| `geofence.event` | `{circleId, placeId, placeName, userId, type: ARRIVE or LEAVE, at}` | members allowed to see that person |
| `quest.progress` | `{userQuestId, questName, progress, target, completed}` | the player |
| `achievement.unlocked` | `{code, name, rarity, rewardXp}` | the player |
| `level.up` | `{from, to, collectibles}` | the player |
| `territory.update` | `{territoryId, name, ownerCircleId, previousOwnerCircleId}` | members of both circles |
| `notification` | same as a notifications list item | the recipient |

Reconnect behaviour (Android client): exponential backoff starting at 1 s, capped at 60 s, with equal jitter; after reconnecting it re-subscribes to every circle and refreshes member locations over REST, because events sent while disconnected are not replayed. The server pings every 25 s and drops sockets that miss a pong.
