#!/usr/bin/env python3
"""Minecraft 1.20.1 Forge: actual world-to-disk save -> server stop -> restart.

Requires Java 17, ForgeGradle/SSN/FLib dependencies, and an isolated CI runner.
No fake NBT round trip is accepted as proof of persistence.
"""
from __future__ import annotations

import os
from pathlib import Path
import re
import socket
import struct
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
RUN_DIR = ROOT / "run"
LOG_DIR = ROOT / "build" / "persistence-smoke"
PASSWORD = "ssn-bugfixes-local-ci-only"
PORT = 25575
POS = "0 80 0"
MASTER = "0 80 1"
MAX_START_SECONDS = 330


def receive_exact(sock: socket.socket, count: int) -> bytes:
    buf = bytearray()
    while len(buf) < count:
        data = sock.recv(count - len(buf))
        if not data:
            raise ConnectionError("RCON closed during response")
        buf.extend(data)
    return bytes(buf)


class Rcon:
    def __init__(self, sock: socket.socket) -> None:
        self.sock = sock
        self.number = 40

    def call(self, command: str, auth: bool = False) -> str:
        self.number += 1
        request_id = self.number
        payload = struct.pack("<ii", request_id, 3 if auth else 2)
        payload += command.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)
        size = struct.unpack("<i", receive_exact(self.sock, 4))[0]
        if size <= 0 or size > 1048576:
            raise ValueError(f"Invalid RCON packet length: {size}")
        body = receive_exact(self.sock, size)
        response_id, packet_type = struct.unpack("<ii", body[:8])
        if response_id == -1:
            raise PermissionError("RCON auth rejected")
        if response_id != request_id:
            raise RuntimeError(f"Wrong RCON response id: {response_id} != {request_id}")
        return body[8:-2].decode("utf-8", errors="replace")

    def close(self) -> None:
        self.sock.close()


def run_server(phase: str) -> tuple[subprocess.Popen, object]:
    log_path = LOG_DIR / f"server-{phase}.log"
    logfile = log_path.open("wb")
    cmd = ["./gradlew", "--no-daemon", "--console=plain", "runServer"]
    print(f"SSN_PERSISTENCE {phase}: starting {cmd!r}; log={log_path}", flush=True)
    proc = subprocess.Popen(cmd, cwd=ROOT, stdin=subprocess.DEVNULL,
                            stdout=logfile, stderr=subprocess.STDOUT)
    return proc, logfile


def wait_for_server(proc: subprocess.Popen, phase: str) -> Rcon:
    deadline = time.monotonic() + MAX_START_SECONDS
    while time.monotonic() < deadline:
        if proc.poll() is not None:
            raise RuntimeError(f"{phase}: server exited before RCON, code {proc.returncode}")
        try:
            sock = socket.create_connection(("127.0.0.1", PORT), timeout=2)
            sock.settimeout(8)
            conn = Rcon(sock)
            conn.call(PASSWORD, auth=True)
            response = conn.call("list")
            if "players" not in response.lower() and "online" not in response.lower():
                raise RuntimeError(f"Unexpected list response: {response}")
            print(f"SSN_PERSISTENCE {phase}: server ready via RCON: {response}", flush=True)
            return conn
        except (OSError, ConnectionError, RuntimeError):
            time.sleep(2)
    raise TimeoutError(f"{phase}: RCON did not become ready in {MAX_START_SECONDS}s")


def issue(rcon: Rcon, command: str) -> str:
    result = rcon.call(command)
    print(f"SSN_PERSISTENCE cmd={command!r} response={result[:700]!r}", flush=True)
    if re.search(r"(unknown command|incorrect argument|not a valid|unknown block|could not|failed)", result, re.I):
        raise AssertionError(f"Command failed: {command!r}: {result}")
    return result


def read_terminal(rcon: Rcon) -> str:
    return issue(rcon, f"data get block {POS}")


def assert_serialized(snapshot: str, phase: str) -> None:
    # Check what the *running Minecraft server actually serialized*, not
    # just a Java object constructed in our test harness.
    required = ["autoFocus", "storagenetworkdir", "storagenetworkjei",
                "storagenetworksort", "connectable", "master"]
    missing = [token for token in required if token not in snapshot]
    if missing:
        raise AssertionError(f"{phase}: terminal NBT missing {missing}: {snapshot}")
    checks = [
        (r"\bautoFocus:\s*0b\b", "autofocus=false"),
        (r"\bstoragenetworkdir:\s*1b\b", "direction=true"),
        (r"\bstoragenetworkjei:\s*1b\b", "jei-sync=true"),
    ]
    for pattern, label in checks:
        if not re.search(pattern, snapshot):
            raise AssertionError(f"{phase}: terminal {label} lost: {snapshot}")
    print(f"SSN_PERSISTENCE {phase}: all required NBT fields present", flush=True)


def stop_server(rcon: Rcon, proc: subprocess.Popen, phase: str) -> None:
    try:
        issue(rcon, "save-all flush")
        # Stop can close the socket before replying; a missing response
        # after 'stop' is normal. Never treat it as a failed save.
        try:
            rcon.call("stop")
        except (OSError, ConnectionError):
            pass
    finally:
        rcon.close()
    try:
        code = proc.wait(timeout=80)
    except subprocess.TimeoutExpired:
        proc.terminate()
        raise AssertionError(f"{phase}: server did not shut down cleanly")
    if code != 0:
        raise AssertionError(f"{phase}: Forge runServer returned exit code {code}")


def prepare() -> None:
    RUN_DIR.mkdir(parents=True, exist_ok=True)
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    (RUN_DIR / "eula.txt").write_text("eula=true\n")
    (RUN_DIR / "server.properties").write_text(
        "enable-rcon=true\n"
        f"rcon.password={PASSWORD}\n"
        f"rcon.port={PORT}\n"
        "enable-query=false\n"
        "server-port=25585\n"
        "server-ip=127.0.0.1\n"
        "online-mode=false\n"
        "enforce-secure-profile=false\n"
        "spawn-protection=0\n"
        "level-name=ssn-persistence-test\n"
        "level-type=minecraft:flat\n"
        "max-tick-time=-1\n"
        "motd=SSN-BugFixes isolated persistence regression\n"
    )


def main() -> None:
    prepare()
    for phase in ("write", "reload"):
        proc, logfile = run_server(phase)
        conn = None
        try:
            conn = wait_for_server(proc, phase)
            if phase == "write":
                issue(conn, f"setblock {MASTER} storagenetwork:master")
                issue(conn, f"setblock {POS} storagenetwork:request")
                # Wait for TileMain tick to set actual network link.
                snapshot = ""
                for _ in range(20):
                    snapshot = read_terminal(conn)
                    if "connectable" in snapshot and "master" in snapshot:
                        break
                    time.sleep(2)
                if "master" not in snapshot:
                    raise AssertionError("Request terminal did not connect to its adjacent master")
                issue(conn, f"data merge block {POS} "
                            "{autoFocus:0b,storagenetworkdir:1b,"
                            "storagenetworkjei:1b,storagenetworksort:0}")
            snapshot = read_terminal(conn)
            assert_serialized(snapshot, phase)
            stop_server(conn, proc, phase)
            conn = None
        finally:
            if conn is not None:
                conn.close()
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    proc.kill()
            logfile.close()
        if phase == "write":
            region = RUN_DIR / "ssn-persistence-test" / "region"
            if not any(region.glob("r.*.mca")):
                raise AssertionError(f"No Minecraft region files written into {region}")
            print("SSN_PERSISTENCE write: region file exists; restarting full JVM", flush=True)
    print("SSN_PERSISTENCE PASS: terminal connectable and UI NBT survived FULL server restart", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"SSN_PERSISTENCE FAIL: {error}", file=sys.stderr, flush=True)
        for path in sorted(LOG_DIR.glob("server-*.log")):
            print(f"--- {path} (last 120 lines) ---", file=sys.stderr)
            text = path.read_text(errors="replace")
            print("\n".join(text.splitlines()[-120:]), file=sys.stderr)
        raise
