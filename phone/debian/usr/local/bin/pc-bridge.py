#!/usr/bin/env python3
"""phone-lab PC bridge.

The phone listens for one target PC (Linux or Windows) on the LAN. The PC
runs a tiny bootstrap script that connects back, then this bridge relays
commands and their output. Local clients (pcsh, the Android app) talk to a
control socket bound to 127.0.0.1 only.

Wire format (PC side, one line per message):
    HELLO <token> <os> <base64(hostname)>     PC -> bridge, once
    <id> <base64(command)>                    bridge -> PC
    <id> <rc> <base64(output)>                PC -> bridge

Control protocol (local side, JSON per line):
    {"op":"status"}                          -> bridge/PC status + token
    {"op":"exec","cmd":"...","timeout":60}   -> {"ok":true,"rc":0,"out":"..."}
    {"op":"ping"}                            -> {"ok":true,"pong":true}
"""
import asyncio
import base64
import json
import os
import socket
import subprocess
import sys
import time
VERSION = "1"
PC_PORT = int(os.environ.get("PL_PC_PORT", "4444"))
CTL_PORT = int(os.environ.get("PL_PC_CTL_PORT", "4445"))
BIND = os.environ.get("PL_BIND", "0.0.0.0")
TOKEN_FILE = os.environ.get(
    "PL_TOKEN_FILE", "/root/.config/phone-lab/pc-token"
)
MAX_OUTPUT = 262_144
def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)
def load_token():
    try:
        with open(TOKEN_FILE, encoding="utf-8") as f:
            token = f.read().strip()
        if token:
            return token
    except OSError:
        pass
    token = base64.urlsafe_b64encode(os.urandom(12)).decode().rstrip("=")
    os.makedirs(os.path.dirname(TOKEN_FILE), exist_ok=True)
    with open(TOKEN_FILE, "w", encoding="utf-8") as f:
        f.write(token + "\n")
    os.chmod(TOKEN_FILE, 0o600)
    return token
def lan_ip():
    """Pick a LAN address, preferring Wi-Fi/Ethernet over VPN tunnels."""
    try:
        out = subprocess.check_output(
            ["ip", "-o", "-4", "addr", "show"], text=True, timeout=3
        )
    except (OSError, subprocess.SubprocessError):
        out = ""
    candidates = []
    for line in out.splitlines():
        parts = line.split()
        if len(parts) < 4:
            continue
        iface = parts[1].rstrip(":")
        if iface == "lo" or iface.startswith(("tun", "dummy", "ifb")):
            continue
        try:
            addr = parts[parts.index("inet") + 1].split("/")[0]
        except (ValueError, IndexError):
            continue
        candidates.append((iface, addr))
    for prefix in ("wlan", "eth", "en"):
        for iface, addr in candidates:
            if iface.startswith(prefix):
                return addr
    if candidates:
        return candidates[0][1]
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(("8.8.8.8", 80))
        addr = sock.getsockname()[0]
        sock.close()
        return addr
    except OSError:
        return ""


class Bridge:
    def __init__(self, token):
        self.token = token
        self.pc_writer = None
        self.pc_info = None
        self.pending = {}
        self.next_id = 1
        self.send_lock = asyncio.Lock()
        self.started = time.time()

    def status(self):
        return {
            "ok": True,
            "running": True,
            "version": VERSION,
            "token": self.token,
            "ip": lan_ip(),
            "pc_port": PC_PORT,
            "ctl_port": CTL_PORT,
            "uptime": int(time.time() - self.started),
            "pc": self.pc_info or {"connected": False},
        }

    async def handle_pc(self, reader, writer):
        peer = writer.get_extra_info("peername")
        peer_ip = peer[0] if peer else "?"
        try:
            hello = await asyncio.wait_for(reader.readline(), 10)
            parts = hello.decode("utf-8", "replace").strip().split(" ", 3)
            if len(parts) < 4 or parts[0] != "HELLO" or parts[1] != self.token:
                log(f"pc {peer_ip}: rejected hello {hello[:80]!r}")
                writer.close()
                return
            os_name = parts[2][:32]
            try:
                host = base64.b64decode(parts[3]).decode("utf-8", "replace")
            except Exception:
                host = ""
            log(f"pc connected {peer_ip} os={os_name} host={host}")

            if self.pc_writer is not None:
                try:
                    self.pc_writer.close()
                except Exception:
                    pass
                for fut in list(self.pending.values()):
                    if not fut.done():
                        fut.set_exception(ConnectionError("pc reconnected"))
                self.pending.clear()
            self.pc_writer = writer
            self.pc_info = {
                "connected": True,
                "os": os_name,
                "host": host[:128],
                "peer": peer_ip,
                "since": int(time.time()),
            }
            while True:
                line = await reader.readline()
                if not line:
                    break
                self.handle_pc_line(line)
        except (asyncio.TimeoutError, ConnectionError, OSError) as exc:
            log(f"pc {peer_ip}: {exc}")
        finally:
            if self.pc_writer is writer:
                self.pc_writer = None
                self.pc_info = None
                log(f"pc disconnected {peer_ip}")
                for fut in list(self.pending.values()):
                    if not fut.done():
                        fut.set_exception(ConnectionError("pc disconnected"))
                self.pending.clear()
            try:
                writer.close()
            except Exception:
                pass

    def handle_pc_line(self, line):
        try:
            cid_s, rc_s, b64 = line.decode("utf-8", "replace").strip().split(" ", 2)
            cid = int(cid_s)
            rc = int(rc_s)
            out = base64.b64decode(b64).decode("utf-8", "replace") if b64 else ""
        except Exception as exc:
            log(f"bad pc line: {exc}")
            return
        if len(out) > MAX_OUTPUT:
            out = out[:MAX_OUTPUT] + "\n[output truncated]"
        fut = self.pending.pop(cid, None)
        if fut is not None and not fut.done():
            fut.set_result((rc, out))

    async def exec_cmd(self, cmd, timeout=60.0):
        if self.pc_writer is None:
            return {"ok": False, "error": "PC is not connected"}
        loop = asyncio.get_running_loop()
        fut = loop.create_future()
        async with self.send_lock:
            cid = self.next_id
            self.next_id += 1
            self.pending[cid] = fut
            payload = base64.b64encode(cmd.encode("utf-8")).decode()
            try:
                self.pc_writer.write(f"{cid} {payload}\n".encode("utf-8"))
                await self.pc_writer.drain()
            except Exception as exc:
                self.pending.pop(cid, None)
                return {"ok": False, "error": f"send failed: {exc}"}
        t0 = time.time()
        try:
            rc, out = await asyncio.wait_for(fut, timeout)
        except asyncio.TimeoutError:
            self.pending.pop(cid, None)
            return {"ok": False, "error": f"timeout after {timeout:g}s"}
        except Exception as exc:
            return {"ok": False, "error": str(exc)}
        return {
            "ok": True,
            "rc": rc,
            "out": out,
            "ms": int((time.time() - t0) * 1000),
        }

    async def handle_ctl(self, reader, writer):
        try:
            while True:
                line = await reader.readline()
                if not line:
                    break
                try:
                    req = json.loads(line.decode("utf-8"))
                except Exception:
                    resp = {"ok": False, "error": "bad json"}
                else:
                    op = req.get("op", "")
                    if op == "status":
                        resp = self.status()
                    elif op == "exec":
                        cmd = str(req.get("cmd", ""))
                        try:
                            timeout = float(req.get("timeout", 60))
                        except (TypeError, ValueError):
                            timeout = 60.0
                        timeout = min(max(timeout, 1.0), 3600.0)
                        if not cmd.strip():
                            resp = {"ok": False, "error": "empty command"}
                        else:
                            resp = await self.exec_cmd(cmd, timeout)
                    elif op == "ping":
                        resp = {"ok": True, "pong": True}
                    else:
                        resp = {"ok": False, "error": f"unknown op {op!r}"}
                writer.write(
                    (json.dumps(resp, ensure_ascii=False) + "\n").encode("utf-8")
                )
                await writer.drain()
        except (ConnectionError, OSError):
            pass
        finally:
            try:
                writer.close()
            except Exception:
                pass


async def main():
    token = load_token()
    bridge = Bridge(token)
    pc_server = await asyncio.start_server(bridge.handle_pc, BIND, PC_PORT)
    ctl_server = await asyncio.start_server(bridge.handle_ctl, "127.0.0.1", CTL_PORT)
    log(
        f"bridge v{VERSION} pc={BIND}:{PC_PORT} ctl=127.0.0.1:{CTL_PORT} "
        f"ip={lan_ip()}"
    )
    async with pc_server, ctl_server:
        await asyncio.gather(
            pc_server.serve_forever(), ctl_server.serve_forever()
        )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        sys.exit(0)
