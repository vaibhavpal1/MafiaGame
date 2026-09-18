# Mafia Party Game — starter Android project

![License: MIT](https://img.shields.io/badge/license-MIT-D4AF37.svg)
![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84.svg)
![Min SDK 23](https://img.shields.io/badge/minSdk-23-D7263D.svg)
![Kotlin](https://img.shields.io/badge/language-Kotlin-7F52FF.svg)
[![Available on itch.io](https://static.itch.io/images/badge-color.svg)](https://excelsiorgrape.itch.io/mafiagame)

This app is a way of playing the card game Mafia, without pushing one
player out of the game to be the host.

A minimal, working implementation of the local WiFi/Hotspot Mafia game:
Mafia (Don), Doctor (Munna Bhai), Cop (Chulbul Pandey), everyone else
Villager (Majdoor) — plus a secret Right Hand once 7+ players join. One
phone hosts and narrates via text-to-speech; the rest connect as players
over Nearby Connections (WiFi/Hotspot/Bluetooth, handled automatically
by Google's API).

### The Right Hand (7+ players only)

A hidden fifth role that's secretly loyal to the Don:

- On night 1, the Don and the Right Hand privately learn who each other
  are — nobody else ever finds out.
- The Right Hand plays every day exactly like a villager: joins
  discussion, votes, and quietly steers blame away from the Don.
- He never acts at night while the real Don is alive, and wins only if
  the Mafia side wins.
- If the town votes out the Don while the Right Hand is still alive, the
  Right Hand secretly takes over as Don. The very next night is a free
  pass for the town (nobody dies, since the family has no killer that
  night) — but from the night after that, the Right Hand kills as the
  new Don, and the game continues until he's caught or the Mafia side
  wins outright.

## How to open it

1. Install **Android Studio** (Hedgehog or newer).
2. Open the `MafiaGame` folder as a project (File → Open).
3. Let Gradle sync — it will download the dependencies listed in
   `app/build.gradle` automatically.
4. Connect physical Android phones via USB (or WiFi debugging) — **you
   need at least 5 real phones**, not emulators. Nearby Connections does
   not work reliably on the emulator.

## How to test it

1. Install the app on 5+ phones.
2. On one phone, tap **Host Game**.
3. On the rest, enter a name and tap **Join Game**.
4. Once 5+ show up on the host's lobby screen, tap **Start Game**.
5. The host phone will speak the night sequence out loud; each player's
   phone shows buttons only when it's their turn to act.

Before testing, on every phone:
- Turn on **Location** and **Bluetooth** (Nearby Connections needs both,
  even though the actual data may flow over WiFi).
- Turn off aggressive **battery optimization / background restriction**
  for the app (common on Xiaomi, Realme, Oppo, Vivo) — otherwise the OS
  can kill the connection while the screen is off or the app is
  backgrounded.

## What's implemented

- Host/Join lobby using Nearby Connections (`P2P_STAR` strategy).
- Random secret role assignment sent privately to each phone.
- Full night loop: Mafia kill → Doctor save → Cop guess, each gated to
  only that role's phone, with the 5-second pauses and text-to-speech
  lines from your spec.
- Kill/save resolution ("nobody was killed" when doctor saves the
  mafia's target) and cop-correct/incorrect announcement.
- 3-minute discussion timer, then a vote (eliminate or skip), tallied
  on the host.
- Win check (Mafia caught → villagers win; Mafia ≥ everyone else →
  mafia wins) that loops back into another night if neither is true.
- Right Hand role (7+ players): mutual reveal with the Don on night 1,
  a one-night "no kill" grace period and secret promotion to Don if the
  original Don is voted out, and win-condition parity that counts him
  as mafia-aligned throughout.

## Known limitations / what to harden next

- **Auto-accept connections**: the host accepts every connection
  request instantly. Fine for a private party; add a manual
  accept/reject list if you want to block strangers.
- **No reconnect handling**: if a player's phone disconnects mid-game
  (app killed, WiFi drop), the host just removes them and moves on —
  it doesn't pause or let them rejoin. Worth adding once the core loop
  feels solid.
- **Timeouts**: each night action has a 15s window, voting has 30s. If
  nobody taps in time the action is skipped (treated as no kill / no
  save / no guess / no vote). Tune these to your group's pace.
- **Vote-collection handling** in `HostActivity.dayPhase()` temporarily
  swaps `nearby.onPayloadReceived` to gather all votes, then restores
  it. This works but is a simple stopgap — a cleaner version would
  route all incoming payloads through one central dispatcher keyed by
  message type instead of temporarily rebinding the callback.
- **No persistence**: closing the host app mid-game loses all state.
  Add a "Play Again" flow once you're happy with one full round.
