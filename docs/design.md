# Design notes

Direction comes from the PRD (section 10) and choices confirmed by the product owner: navy foundation, teal as the single accent, warm amber reserved for XP and rewards, text wordmark and a placeholder launcher icon until a real logo exists.

**Design read:** a consumer location-social game for couples, families and friends, in a calm navy, map-first visual language with game accents. Dials: ENERGY 2 / RHYTHM 2 / MOTION 2.

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

## Motion

| Where | What | Why |
| --- | --- | --- |
| Tab switches, list scrolling, map | No decorative motion | Used dozens of times a day |
| Screen push | 160 ms fade in, 120 ms fade out, strong ease-out | Prevents a jarring jump without slowing navigation |
| Tile track | Fill eases to its new value over 260 ms (ease-in-out) | State indication: shows how much progress was just added |
| Level-up dialog | Number scales from 0.9 and fades in, 260 ms ease-out | Rare, earned moment |
| System "Remove animations" setting | All of the above become instant | Reduced motion |

## Contrast (WCAG AA)

Measured ratios for the main pairs: body text on navy 15.7:1, secondary text on navy 8.9:1, teal on navy 8.7:1, teal (light theme) on paper 5.4:1, secondary text (light) on paper 6.8:1, amber on navy 10:1, dark amber on paper 5.7:1, white initials on every avatar color 5.3:1 or more, input outlines 3:1 or more against their backgrounds.

## Open items

- Real logo and launcher icon (currently the word "Locana" and a plain letter L, marked as placeholders in the resources).
- Visual review on real devices. Every main screen has been rendered and reviewed in light and dark through the Robolectric click-through (see `docs/android.md`), which led to a visible selection ring on the avatar picker and hidden distances when no location fix exists. The Google Map layer itself has not been rendered (no Maps key in that run).
