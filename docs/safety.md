# Safety: SOS, crash detection, quick messages, alerts

## SOS
- The red SOS button on the map must be held for 1.5 s (a tap only shows a hint; Talkback gets a long-press action).
- `POST /v1/sos {reason: MANUAL|CRASH, latitude, longitude, accuracyM}` (5 per 10 minutes per user):
  - an in-app `SOS` notification with a Maps link goes to everyone who shares a circle with the sender (blocked people excluded);
  - an e-mail goes to those members and to every emergency contact that has an address (needs `SMTP_URL`; in development it is logged);
  - the response lists the emergency contacts' phone numbers; the phone dials the first one (`ACTION_DIAL`, or `ACTION_CALL` if the person turned on "Place the emergency call myself" and granted `CALL_PHONE`).
- SOS is the one notification that carries coordinates: someone asking for help has chosen to be found.
- Emergency contacts: `GET/PUT /v1/emergency-contacts` (at most five, private to their owner). Profile → Safety.

## Crash detection
- The accelerometer is listened to only while the person seems to be in a vehicle (activity "vehicle", or a fix of 6 m/s or more in the last minute).
- A reading of 5 g or more asks "Are you okay?" full screen (also over the lock screen), with an "I'm OK" action on the notification. 15 seconds without an answer sends `SOS` with reason `CRASH`. One question per two minutes at most.
- The countdown runs in the app process (kept alive by the tracking service), not in the screen.
- Switch off under Profile → Safety. Thresholds live in `CrashDetector` (domain module, unit tested). They have not been tuned on real crashes: expect to adjust them from field reports.

## Quick messages
- A fixed list (`GET /v1/quick-messages`); nothing typed by the sender reaches the recipient.
- `POST /v1/circles/:circleId/members/:userId/quick-message {code}`: both must be members and not blocked; one message per 5 s per pair, 60 per hour.
- The recipient gets a `QUICK_MESSAGE` notification. In the app it plays as a banner with falling emoji matching the message; in the background it is a system notification.

## Notifications
- Long trips (`TRIP`): when a trip reaches 15 km, or 40 minutes and 5 km, everyone who can see the person is told once per trip (distance and mode, no coordinates). Per-person switch: Notifications → Long trips (`notifyTrips`).
- Place arrivals (`ARRIVAL`), quick messages and SOS arrive within seconds while the tracking service keeps the live connection open, and otherwise at the next 15-minute background sync. There is no Firebase push yet: a phone that has fully stopped Locana will only learn about them at the next sync or when the app opens.

## Driving behaviour (trip replay)
- While the phone seems to be in a vehicle, positions are asked for every 3 s (only some are uploaded) so braking can be seen. `DrivingBehavior` (domain module, unit tested) flags: **hard braking** (speed falling faster than 3.5 m/s², from at least 6 m/s), **rapid acceleration** (rising faster than 3 m/s²), and **phone use** (screen on and unlocked for 12 s while moving at 5 m/s or more, at most once a minute). A passenger using the phone looks the same, so the label is "phone use", not "distracted driver".
- Flagged moments are uploaded with the fix (`driveEvent`), kept in `DriveEvent`, and counted on each trip in the timeline (`eventCounts`, `events`). Tapping a trip opens a full-screen replay: the route, a bar to scrub or play through the trip's times, and the events on the route.

## Monsters, weapons, shop
- With exploration on, after travelling at least 250 m and at least 8 minutes since the last one, a monster appears with 35% chance (one at a time, 15 minutes to answer). Monsters scale with level (`battle/catalog.ts`).
- Fights are turns: you choose a weapon, the server rolls the damage (15% spread, 12% critical) and the monster's counter-attack. Winning pays coins and XP (XP is never spent, so levels never go down; weapons need a level instead). Losing costs nothing.
- Coins also come from the first visit of each landmark (15). Weapons: `GET /v1/shop`, `POST /v1/shop/buy`. Basic attack (power 8) is free.
- To try a fight without walking: `POST /v1/battle/dev-spawn {latitude, longitude, monster?}`. It is refused when `NODE_ENV=production`.
