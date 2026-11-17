#!/usr/bin/env python3
"""Local MOCK relay for testing Vanish without the class relay. Python stdlib only.

NOT the class relay. It implements the protocol ASSUMED in docs/PROTOCOL_ASSUMED.md:
 - stamps sent_at / expires_at, broadcasts to everyone (including the sender),
 - ignores a re-sent message id, keeps messages in memory only, announces expiry,
 - never logs message text.
Usage: python3 mock_relay.py [--port 8765] [--ttl-ms 30000]
"""
import argparse, asyncio, base64, hashlib, json, struct, time

GUID = b"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
clients = {}      # writer -> name
messages = {}     # id -> expires_at (memory only; text is never kept after broadcast)
TTL = 30000

def now_ms(): return int(time.time() * 1000)

def frame(text: str) -> bytes:
    p = text.encode(); n = len(p)
    h = b"\x81" + (bytes([n]) if n < 126 else b"\x7e" + struct.pack(">H", n) if n < 65536 else b"\x7f" + struct.pack(">Q", n))
    return h + p

async def read_frame(r):
    b1, b2 = await r.readexactly(2)
    op, n = b1 & 0x0F, b2 & 0x7F
    if n == 126: n = struct.unpack(">H", await r.readexactly(2))[0]
    elif n == 127: n = struct.unpack(">Q", await r.readexactly(8))[0]
    mask = await r.readexactly(4) if b2 & 0x80 else b"\0\0\0\0"
    data = bytearray(await r.readexactly(n))
    for i in range(n): data[i] ^= mask[i % 4]
    return op, bytes(data)

async def broadcast(obj):
    data = frame(json.dumps(obj))
    for w in list(clients):
        try: w.write(data); await w.drain()
        except Exception: clients.pop(w, None)

async def expire_later(mid):
    await asyncio.sleep(TTL / 1000)
    messages.pop(mid, None)
    await broadcast({"type": "expired", "id": mid})

async def handle(r, w):
    try:
        head = (await r.readuntil(b"\r\n\r\n")).decode()
        key = [l.split(":", 1)[1].strip() for l in head.split("\r\n") if l.lower().startswith("sec-websocket-key")][0]
        acc = base64.b64encode(hashlib.sha1(key.encode() + GUID).digest()).decode()
        w.write(f"HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: {acc}\r\n\r\n".encode())
        await w.drain()
        while True:
            op, data = await read_frame(r)
            if op == 8: break
            if op == 9: w.write(b"\x8a\x00"); continue
            if op != 1: continue
            try: m = json.loads(data)
            except Exception: continue
            t = m.get("type")
            if t == "hello":
                clients[w] = m.get("name", "?")
                w.write(frame(json.dumps({"type": "welcome", "server_time": now_ms()}))); await w.drain()
            elif t == "ping":
                w.write(frame(json.dumps({"type": "pong"}))); await w.drain()
            elif t == "message" and w in clients:
                mid = m.get("id")
                if not mid or mid in messages: continue           # duplicate id: ignored
                sent = now_ms()
                messages[mid] = sent + TTL
                asyncio.create_task(expire_later(mid))
                await broadcast({"type": "message", "id": mid, "sender": m.get("sender"),
                                 "text": m.get("text"), "sent_at": sent, "expires_at": sent + TTL})
    except (asyncio.IncompleteReadError, ConnectionError):
        pass
    finally:
        clients.pop(w, None)
        w.close()

async def main(port):
    srv = await asyncio.start_server(handle, "0.0.0.0", port)
    print(f"mock relay on :{port}, ttl={TTL}ms (message text is never logged)")
    async with srv: await srv.serve_forever()

if __name__ == "__main__":
    ap = argparse.ArgumentParser(); ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--ttl-ms", type=int, default=30000); a = ap.parse_args()
    TTL = a.ttl_ms
    asyncio.run(main(a.port))
