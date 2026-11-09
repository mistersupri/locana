# Design notes

Direction comes from the PRD (section 10) and choices confirmed by the product owner: navy foundation, teal as the single accent, warm amber reserved for XP and rewards, text wordmark and a placeholder launcher icon until a real logo exists.

**Design read:** a consumer location-social game for couples, families and friends, in a "night map" navy language where the map tile is the identity motif. Dials: ENERGY 3 / RHYTHM 3 / MOTION 2 (raised from 2 / 2 / 2 in the November redesign at the owner's request).

## Decisions and reasons

| Decision | Reason |
| --- | --- |
| Navy foundation, light theme as well, user can pick | Navy reads calm and trustworthy for a product about location; both themes exist because people use the map outdoors in bright light |
| Teal is the only accent | One color always means "location, live, act here", so it never competes with anything else |
| Amber only for XP and rewards | Game value is visible but kept apart from safety signals (amber never marks a person's state) |
| Bricolage Grotesque for headings and numbers, platform sans for body | Gives levels, XP and ranks a playful voice of their own; body text stays on the system face for reading comfort, text scaling and script coverage |
| Identity motif: the map tile | Progress bars for XP, quests and challenges are rows of tiles being revealed, the same act the game rewards on the map |
| Radius scale 8 / 14 / 24 dp, buttons 12 dp | Radius signals hierarchy (inputs and chips, cards, sheets); nothing is pill-shaped except status labels |
| Presence encoded by shape, not only color | Solid ring = live, dashed ring = stale, faded with no ring = hidden; readable for color-blind users and in sunlight |
| Initials avatars with six colors | No photos are collected; colors were checked for white-text contrast |
| Elevation only on floating map controls and sheets | Shadows mark what floats above the map; cards on plain screens stay flat |
| Map: one top bar, two small buttons, a member strip | The PRD asks for an uncluttered map; layers and details live in sheets |
| Member strip under the map | A list alternative to markers for accessibility and for builds without a Maps key |
| The privacy promise gets its own panel on onboarding | It is what people need to believe before granting location |
| Rarity drives emphasis on achievements | Epic and legendary unlocks get the reward border; common ones stay quiet |
| One navy "night map" hero panel per main tab, in both themes | Gives each tab exactly one focal point (today's quests, level, the circle, you) and reads as the map at night, where the game happens |
| Oversized display number inside each hero (56 sp) | The single most important number on the tab: quests done today, level, quest progress |
| Tile field lit from real data | Explore lights one tile per tile the player revealed; onboarding shows the idea once. Never decoration without a number beside it |
| Event quest as a wide amber banner | Events are the rarest and most valuable quests; amber is reserved for rewards, so the color already says "prize" |
| Weekly quests in a horizontal row, daily quests as a list | Rhythm: the two sets read as different kinds of goals, and the week reads as one set |
| Square condition icon on every quest | Tells the kind of outing (tiles, landmarks, walk, circle, days, set) before reading |
| Navy share panel on the map | "You're not sharing" is the one action on an unshared map |

## Motion

| Where | What | Why |
| --- | --- | --- |
| Tab switches, list scrolling, map | No decorative motion | Used dozens of times a day |
| Screen push | 160 ms fade in, 120 ms fade out, strong ease-out | Prevents a jarring jump without slowing navigation |
| Cards that act as buttons (quests, Play hero) | Scale to 0.97 while pressed, 120 ms strong ease-out | Feedback: the tap is acknowledged when the finger lands |
| Quest card finishing | Container crossfades to teal over 180 ms | State indication: the quest became claimable |
| Onboarding tile field | Tiles fade in and grow from 0.9, 40 ms stagger, under 900 ms, once | Explanation on a screen seen once |
| Quest claim dialog | XP counts up over 600 ms, strong ease-out | Rare, earned moment |
| Tile track | Fill eases to its new value over 260 ms (ease-in-out) | State indication: shows how much progress was just added |
| Level-up dialog | Number scales from 0.9 and fades in, 260 ms ease-out | Rare, earned moment |
| System "Remove animations" setting | All of the above become instant | Reduced motion |

## Contrast (WCAG AA)

Measured ratios for the main pairs: body text on navy 15.7:1, secondary text on navy 8.9:1, teal on navy 8.7:1, teal (light theme) on paper 5.4:1, secondary text (light) on paper 6.8:1, amber on navy 10:1, dark amber on paper 5.7:1, white initials on every avatar color 5.3:1 or more, input outlines 3:1 or more against their backgrounds.

## Open items

- Real logo and launcher icon (currently the word "Locana" and a plain letter L, marked as placeholders in the resources).
- Visual review on real devices. Every main screen (before and after the redesign) has been rendered and reviewed in light and dark through the Robolectric click-through (see `docs/android.md`), which led to a visible selection ring on the avatar picker and hidden distances when no location fix exists. The Google Map layer itself has not been rendered (no Maps key in that run).

## Map direction: light, floating controls (current)

The Map follows the owner's reference screenshot: a pale full-bleed map, white rounded controls with a soft shadow floating above it, a circle-name pill at the top (filled dot = sharing, hollow = not), pin-shaped member markers whose tip is the position (dashed edge and an age bubble when the position is old), and a draggable bottom sheet with People and Places chips. Only existing features are surfaced: settings, notifications, refresh, centre on me, layers, add menu (invite, place, new circle, join), and the Share, Pause, Resume and Invite pills. Not copied: the reference's brand, colours, photos, driving reports, SOS, check-in, chat and membership upsell, because Locana has none of them. Hero panels are white cards with a soft shadow in light mode.

### Update: violet palette and exact reference layout

At the owner's request the accent is now the reference violet (`Violet700` #5B2DD0 light, `Violet300` dark) and the light theme is white on a faint lavender paper. Layout details copied: 16 dp radius on floating controls, 28 dp sheet, a white status card at the top of the sheet, the circle name in bold, icon-only chips (the selected one is a wide dark pill, the others 56 dp squares), a circled plus, and a white bottom bar with no indicator pill where only the selected tab is violet. The light map style is warm beige land, blue water and white roads. Member pins keep the user's avatar colour, with orange for the amber avatar.

### Update: map pins, timeline, automatic sharing

Member markers are drawn directly onto bitmaps at the screen density, so edges stay sharp, and the marker's anchor is the small dot at the bottom of the pin, which sits exactly on the coordinate. A rounded-square avatar carries a badge (house, walking, car), a bubble says "Here for 3 hrs, 52 mins" or the current speed, and a soft beam shows the phone's heading while moving. Your own pin uses your phone's newest fix and battery, not the last upload. The refresh, locate and add buttons and the sharing card are gone: sharing is automatic inside a circle (see privacy.md), and new circles and codes live in the circle switcher. Each member has a "Places and trips" timeline with route maps.

### Update: one map screen, no profile tab

The Profile tab is gone: the gear on the map opens the profile page (photo, edit, notifications, privacy, account, theme, sign out). There is no location sharing page, because sharing is automatic. Tapping a pin or a member row focuses that person: the sheet opens with their name, battery, what they are doing (at a place since when, driving, walking) and their day of stays and trips with route maps, and the camera lifts their pin above the sheet. The back arrow returns to the circle. Photos replace initials in avatars and in pins. The light map style now shows place names and icons.

### Update: honest movement and heading

The badge and the "Walking" bubble need two independent signals to agree (a clear speed and a real step), on the phone and on the server, so a phone lying on a desk is never shown as walking. A still phone now checks in every two minutes without a distance filter, which is what gives stays a length and keeps the pin live. The beam under a pin follows the phone's compass: your own pin turns live, others show the heading of their last upload (GPS bearing while travelling, compass otherwise). Timeline cards say "Stayed 3 hrs, 52 mins" and mark the newest entry "Happening now".

### Update: driving, walking and standing still

What a pin says now comes from the phone's activity recognition when allowed, then from evidence the server can check (vehicle speed with distance, remembered for five minutes so lights and traffic do not break a drive, or a walking pace backed by a real step). The same verdict feeds the pin, the bubble, the panel status and the timeline labels (drive, ride, walk). When a day has positions but no stay or trip yet, the panel says how many positions it has instead of "nothing recorded".

### Update: places around you, following a person, steadier layers

Restaurants, shops and buildings drawn by Google are tappable and open a card (name, kind, distance, rating, open now, address, Directions, Open in Maps). Four category chips (Food, Cafés, Shops, Sights) list places around the middle of the map with emoji markers; tapping a category again clears it. A focused person is followed: the map is padded by the sheet, so their pin stays centred in the visible strip while they move, and their day reloads every 15 seconds. Explored tiles and landmarks are cached as the map moves and requested with a margin around the screen, so panning back no longer waits for the network. Exploration now starts on for new accounts (tiles stay private), and 15 more Greater Jakarta landmarks give players outside the centre something to visit.
