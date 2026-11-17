# Test plan (do each, tick it, and take a screenshot/notes for the design note)

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | Two phones/emulators, send a message | both show it; countdowns within ~1 s | |
| 2 | Home button for 10 s, return | countdown has continued (not reset, not paused) | |
| 3 | Rotate during countdown | countdown unchanged (manifest handles config change; time from expires_at) | |
| 4 | Swipe app away during countdown, reopen after expiry | message gone on start (sweeper runs in `VanishApp.onCreate`) | |
| 5 | Set phone clock +10 min (Settings → Date), reconnect, receive | countdown still ≈30 s | |
| 6 | Airplane mode, send, turn network on within 30 s | message shows "waiting", then arrives once | |
| 7 | Airplane mode, send, turn network on after 50 s | message dropped with notice, nothing sent | |
| 8 | Kill relay connection / restart relay | app reconnects (backoff ≤15 s), no duplicates | |
| 9 | Re-send same id (mock relay / second team) | ignored; shows once | |
| 10 | After expiry, inspect DB (below) | no row, no text | |

## Verify deletion (Stage 4 question)
```
adb shell run-as com.example.vanish sqlite3 databases/vanish.db "select * from messages;"
adb shell run-as com.example.vanish sh -c 'strings databases/vanish.db* | grep -i "<your test text>"'
```
(Use a debug build. If `sqlite3` is missing on the device, `adb exec-out run-as com.example.vanish cat databases/vanish.db > v.db`
then inspect on your computer.) Do it once right after sending (text present) and once after expiry (text gone).
`PRAGMA secure_delete=ON` and TRUNCATE journal mode are why the bytes are gone, not just unlinked.

# 5-minute demo script
1. (0:00) Two devices on the class relay. State the relay address, show `Config.kt` line.
2. (0:30) Send from A; show both screens counting down; point out the ~equal seconds and say why (same `expires_at`).
3. (1:30) Message expires; run the `select * from messages` command – empty. Show the earlier output with the row.
4. (2:30) Airplane mode on A, send "offline test", show "waiting for network"; turn it on; it arrives once on B.
5. (3:30) Show a message queued > 30 s: dropped notice. Explain the 0:20 / 0:50 rule.
6. (4:00) Show `ClockSync.kt`; mention the wrong-clock test result.
7. (4:30) One honest limitation (screenshots) and one thing you'd change.

# User-test notes (Stage 6) – fill in
- Person, task wording, hesitations, surprises:
- Change made because of it (and its cause):
- Did they expect the disappearance time? What did they assume?
  (Likely finding to check for: a message waiting offline has no countdown yet, which can look like it "doesn't vanish".)
