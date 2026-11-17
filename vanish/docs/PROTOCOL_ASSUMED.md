# Protocol this app implements (ASSUMED – replace with the class spec)

I did not have the class protocol document, so the app uses the following JSON-over-WebSocket
protocol. **Everything wire-related lives in `Protocol.kt`; the address lives in `Config.kt`.**
After the Day 2 protocol review, edit those two files only.

| Direction | Frame | Notes |
|---|---|---|
| client → relay | `{"type":"hello","version":1,"name":"…","device_id":"…"}` | first frame after connecting |
| relay → client | `{"type":"welcome","server_time":<ms>}` | app syncs its clock to relay time |
| client → relay | `{"type":"message","id":"<uuid>","sender":"…","text":"…"}` | relay stamps times |
| relay → all (incl. sender) | `{"type":"message","id","sender","text","sent_at","expires_at"}` | echo to sender = "sent" confirmation |
| client → relay | `{"type":"delivered","id":"…"}` | sent by a receiver after storing |
| relay → all | `{"type":"expired","id":"…"}` | expiry announcement |
| both | `{"type":"ping"}` / `{"type":"pong"}` | heartbeat every 10 s; no frame for 25 s ⇒ reconnect |

Times are epoch milliseconds from the relay. `expires_at = sent_at + 30000`.

Questions to raise in the Day 2 review (typical ambiguities):
1. Does the relay echo a message back to its sender? (the app relies on this)
2. Is `sent_at` the time the relay *received* the message or the time it *delivered* it?
3. Does the relay de-duplicate by `id`, and does it replay unexpired messages to a reconnecting client?
4. Time unit (ms vs s) and field names.
5. Is there a `welcome` frame with the relay time? If not, use `sent_at` of the first message to sync.
