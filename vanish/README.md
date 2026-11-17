# Vanish – Class Network client (Android)

A chat client for the class "Vanish" network: every message disappears 30 seconds after delivery, on
every phone, because each phone computes the countdown from the relay's `expires_at`.

- **Platform:** Android, Kotlin, Jetpack Compose, Room (SQLite), OkHttp WebSocket. minSdk 26.
- **Relay address:** set in `app/app/src/main/java/com/example/vanish/Config.kt` (`RELAY_URL`).
  Default `ws://10.0.2.2:8765` is the local mock relay as seen from the Android emulator.
  **Change it to the class relay address before the demo.**
- **Protocol:** see `docs/PROTOCOL_ASSUMED.md`. Wire format is isolated in `Protocol.kt`.

## Build and run
1. Open the folder `app/` in Android Studio (Ladybug or newer, JDK 17). Let Gradle sync
   (plugin versions are pinned in `app/build.gradle.kts`).
2. Run on an emulator or a phone.
3. Optional local test relay (Python 3, no packages needed):
   `python3 relay/mock_relay.py` then `python3 relay/test_relay.py` (protocol self-check).
   On a real phone use your computer's LAN IP in `Config.RELAY_URL`.

## Code map
| File | Job |
|---|---|
| `Config.kt` | relay URL, TTL, heartbeat/reconnect timings |
| `Protocol.kt` | JSON encode/decode (only place that knows the wire format) |
| `ClockSync.kt` | relay time = last relay time + monotonic elapsed time (ignores wrong phone clock) |
| `RelayClient.kt` | WebSocket, heartbeat, reconnect with backoff |
| `ChatRepository.kt` | lifecycle: outbox, duplicate rejection, expiry sweeper, real deletion |
| `data/Entities.kt` | Room tables `messages`, `outbox`, `seen` (ids only), `secure_delete` on |
| `MainActivity.kt` | join screen, chat screen, 250 ms countdown ticker |

## Docs
- `docs/Vanish_Design_Note.pdf` – design note
- `docs/TEST_PLAN_AND_DEMO.md` – tests per stage and the 5-minute demo script
- `docs/REFLECTION_TEMPLATE.md` – individual reflection prompts

## AI / tools used (academic integrity)
- Claude (Anthropic) was used to generate the initial code and documents; I reviewed, ran and can
  explain them. <-- **edit this line to describe exactly what you used it for**
- Libraries: OkHttp, Room, Jetpack Compose, Kotlin coroutines (all standard Android libs).

## Rules followed
Connects only to the agreed relay; does not capture or read other students' traffic; protocol is not
changed locally.
