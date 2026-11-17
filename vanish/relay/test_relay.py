#!/usr/bin/env python3
"""Protocol check against mock_relay.py: join, broadcast, duplicate id ignored, expiry announced."""
import asyncio, base64, json, os, struct, subprocess, sys, time

async def ws_connect(port):
    r, w = await asyncio.open_connection("127.0.0.1", port)
    w.write(b"GET / HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            b"Sec-WebSocket-Key: " + base64.b64encode(os.urandom(16)) + b"\r\nSec-WebSocket-Version: 13\r\n\r\n")
    await r.readuntil(b"\r\n\r\n"); return r, w

def send(w, obj):
    p = json.dumps(obj).encode(); mask = os.urandom(4)
    w.write(b"\x81" + bytes([0x80 | len(p)]) + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(p)))

async def recv(r, timeout=5):
    async def go():
        b1, b2 = await r.readexactly(2); n = b2 & 0x7F
        if n == 126: n = struct.unpack(">H", await r.readexactly(2))[0]
        return json.loads(await r.readexactly(n))
    return await asyncio.wait_for(go(), timeout)

async def main():
    port = 18765
    srv = subprocess.Popen([sys.executable, os.path.join(os.path.dirname(__file__), "mock_relay.py"), "--port", str(port), "--ttl-ms", "1500"], stdout=subprocess.DEVNULL)
    await asyncio.sleep(0.6)
    try:
        ra, wa = await ws_connect(port); rb, wb = await ws_connect(port)
        send(wa, {"type": "hello", "name": "A", "device_id": "a"}); w1 = await recv(ra); assert w1["type"] == "welcome"
        send(wb, {"type": "hello", "name": "B", "device_id": "b"}); await recv(rb)
        send(wa, {"type": "message", "id": "m1", "sender": "A", "text": "hi"})
        ma, mb = await recv(ra), await recv(rb)
        assert ma == mb and mb["expires_at"] - mb["sent_at"] == 1500, mb
        print("PASS broadcast incl. sender echo; expires_at = sent_at + ttl (same on both phones)")
        send(wa, {"type": "message", "id": "m1", "sender": "A", "text": "hi"})     # duplicate resend
        try: await recv(rb, 0.7); raise SystemExit("FAIL duplicate id was re-broadcast")
        except asyncio.TimeoutError: print("PASS re-sent id is ignored by relay (exactly-once on the wire)")
        e = await recv(rb); assert e == {"type": "expired", "id": "m1"}, e
        print("PASS expiry announced to all clients")
    finally:
        srv.kill()
asyncio.run(main())
