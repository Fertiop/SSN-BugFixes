#!/usr/bin/env python3
"""Check a real Minecraft 1.20.1 Forge client joins a real dedicated server.

Runs the SAME compiled SSN-BugFixes, SSN 1.13.2 and FLib in both JVMs.
Uses a disposable offline-mode localhost server and a virtual X11 display.
The acceptance criterion is one actual connected remote player on RCON,
not merely Minecraft process startup or test-only fake ServerPlayer.
"""
from __future__ import annotations

import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time

from persistent_world_smoke import (LOG_DIR, ROOT, Rcon, RUN_DIR, issue,
                                    prepare, run_server, stop_server,
                                    wait_for_server)

CLIENT_DIR = ROOT / "run-client-join"
CLIENT_LOG = LOG_DIR / "client-join.log"
MAX_JOIN_SECONDS = 450


def client_login_count(response: str) -> int:
    match = re.search(r"There are (\d+) of a max of \d+ players online", response)
    if not match:
        raise RuntimeError(f"Unexpected RCON /list response: {response!r}")
    return int(match.group(1))


def start_client() -> tuple[subprocess.Popen, object]:
    CLIENT_DIR.mkdir(exist_ok=True, parents=True)
    log = CLIENT_LOG.open("wb")
    command = [
        "xvfb-run", "-a", "-s", "-screen 0 1280x800x24",
        "./gradlew", "--no-daemon", "--console=plain",
        "-PssnClientJoin", "runClient",
    ]
    # xvfb-run is a shell wrapper; own process group allows robust cleanup.
    print(f"SSN_MULTIPLAYER: starting actual Minecraft client: {command}", flush=True)
    proc = subprocess.Popen(command, cwd=ROOT, stdin=subprocess.DEVNULL,
                            stdout=log, stderr=subprocess.STDOUT,
                            start_new_session=True,
                            env={**os.environ, "LIBGL_ALWAYS_SOFTWARE": "1"})
    return proc, log


def stop_client(proc: subprocess.Popen) -> None:
    if proc.poll() is not None:
        return
    os.killpg(proc.pid, signal.SIGTERM)
    try:
        proc.wait(timeout=22)
    except subprocess.TimeoutExpired:
        os.killpg(proc.pid, signal.SIGKILL)
        proc.wait(timeout=10)


def main() -> None:
    prepare()
    client = None
    client_log = None
    server, server_log = run_server("multiplayer")
    conn: Rcon | None = None
    try:
        conn = wait_for_server(server, "multiplayer")
        issue(conn, "setblock 0 80 1 storagenetwork:master")
        issue(conn, "setblock 0 80 0 storagenetwork:request")
        # Wait for a real SSN master-to-terminal link before logging in.
        linked = False
        for _ in range(15):
            data = issue(conn, "data get block 0 80 0")
            if "master:" in data and "connectable:" in data:
                linked = True
                break
            time.sleep(1)
        if not linked:
            raise AssertionError("SSN request terminal never connected to master")

        client, client_log = start_client()
        deadline = time.monotonic() + MAX_JOIN_SECONDS
        joined = False
        while time.monotonic() < deadline:
            if client.poll() is not None:
                raise AssertionError(f"Minecraft client exited before login (code {client.returncode})")
            if server.poll() is not None:
                raise AssertionError(f"Forge server died before client login (code {server.returncode})")
            online = client_login_count(conn.call("list"))
            if online == 1:
                joined = True
                print("SSN_MULTIPLAYER: /list confirms exactly one REAL client connection", flush=True)
                break
            if online > 1:
                raise AssertionError(f"Unexpected player count: {online}")
            time.sleep(3)
        if not joined:
            raise TimeoutError(f"Real client did not join Forge SSN server in {MAX_JOIN_SECONDS}s")

        # Verify the normal server-side SSN block entity is still accessible
        # after a real client successfully completes Forge handshake.
        time.sleep(5)
        snapshot = issue(conn, "data get block 0 80 0")
        if "connectable:" not in snapshot or "master:" not in snapshot:
            raise AssertionError("Server SSN terminal lost network link after client joined")
        if client.poll() is not None:
            raise AssertionError("Minecraft client exited immediately after login")
        (LOG_DIR / "multiplayer-join-passed.txt").write_text(
            "PASS: real Forge 1.20.1 Minecraft client connected via protocol handshake "
            "to dedicated SSN 1.13.2 + SSN-BugFixes server; RCON /list=1\n")
        print("SSN_MULTIPLAYER PASS: actual Forge handshake + player join and server network access",
              flush=True)

        stop_client(client)
        stop_server(conn, server, "multiplayer")
        conn = None
    finally:
        if client is not None:
            stop_client(client)
        if conn is not None:
            conn.close()
        if server.poll() is None:
            server.terminate()
            try:
                server.wait(timeout=10)
            except subprocess.TimeoutExpired:
                server.kill()
        if client_log is not None:
            client_log.close()
        server_log.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"SSN_MULTIPLAYER FAIL: {exc}", file=sys.stderr, flush=True)
        for path in [LOG_DIR / "server-multiplayer.log", CLIENT_LOG]:
            if path.is_file():
                lines = path.read_text(errors="replace").splitlines()
                print(f"--- {path} (last 95 lines) ---", file=sys.stderr)
                print("\n".join(lines[-95:]), file=sys.stderr)
        raise
