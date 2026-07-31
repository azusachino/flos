#!/usr/bin/env python3

from __future__ import annotations

import socket
import sys
import time

HOST = "localhost"
PORT = 9000
STARTUP_TIMEOUT_SECONDS = 60


def wait_for_echo() -> None:
    deadline = time.monotonic() + STARTUP_TIMEOUT_SECONDS
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            verify_echo()
            return
        except OSError as error:
            last_error = error
            time.sleep(2)
    raise RuntimeError(
        f"containerized event-loop server did not answer before the timeout: {last_error}"
    )


def verify_echo() -> None:
    with socket.create_connection((HOST, PORT), timeout=5) as sock:
        sock.sendall(b"hello")
        response = sock.recv(5)
    if response != b"HELLO":
        raise RuntimeError(f"expected b'HELLO', got {response!r}")


def main() -> int:
    try:
        wait_for_echo()
    except (RuntimeError, OSError) as error:
        print(f"Netty deployment smoke test failed: {error}", file=sys.stderr)
        return 1

    print("netty deployment smoke: containerized event-loop server echoed HELLO")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
