#!/usr/bin/env python3
"""Fake backend for the impression tracking SDK.

Accepts the POST requests the SDK sends, prints every impression it receives and
answers 200 OK so that the SDK deletes the delivered entries from its queue.

Usage:
    python3 tools/fake-impression-server.py [port]

The default port is 8080, which is where the SDK sends impressions out of the
box: inside an Android emulator the address 10.0.2.2 is the loopback interface
of the machine running the emulator.

Uses the Python standard library only - it is a development aid, not part of the
SDK or of its build.
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

DEFAULT_PORT = 8080


class ImpressionHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8")

        self.send_response(200)
        self.send_header("Content-Length", "0")
        self.end_headers()

        print(f"POST {self.path}", flush=True)
        try:
            for impression in json.loads(body)["impressions"]:
                print("   ", json.dumps(impression), flush=True)
        except (ValueError, KeyError):
            print("    unexpected payload:", body, flush=True)

    def log_message(self, *args):
        """Silences the default one-line-per-request access log."""


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    print(f"Listening for impressions on http://0.0.0.0:{port} - Ctrl+C to stop", flush=True)
    try:
        HTTPServer(("0.0.0.0", port), ImpressionHandler).serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.", flush=True)


if __name__ == "__main__":
    main()
