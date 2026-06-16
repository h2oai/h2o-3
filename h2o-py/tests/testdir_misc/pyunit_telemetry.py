import sys
sys.path.insert(1, "../../")
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

from h2o import telemetry as t

# Telemetry needs no running cluster: these tests exercise the wire contract
# (envelope + bucket labels) via a transport stub, plus one end-to-end smoke
# test that proves the background worker actually delivers an event over HTTP.

VERSION = "3.46.0.12"


def _capture_payloads():
    """Swap the transport for an in-memory sink; returns (captured_list, restore_fn)."""
    captured = []
    orig = t._post_async
    t._post_async = lambda payload, enrich=None: captured.append(payload)
    t.set_disabled(False)
    return captured, (lambda: setattr(t, "_post_async", orig))


def telemetry_wire_contract():
    captured, restore = _capture_payloads()
    try:
        t.send_import(VERSION, "hdfs", "csv", "ok",
                      compressed_size_bytes=50 * 1024 * 1024,
                      frame_shape={"rows_bucket": "1K-10K", "cols_bucket": "1-10"})
        t.send_init_telemetry(VERSION)
    finally:
        restore()

    by_event = {e["event"]: e for e in captured}
    assert "import" in by_event, "import event not emitted"
    assert "init" in by_event, "init event not emitted"

    imp = by_event["import"]
    # Common envelope is present on every event.
    for key in ("payload_version", "client", "h2o_version", "session_id", "ts", "product"):
        assert key in imp, "missing envelope key %r" % key
    assert imp["client"] == "python"
    # Numeric inputs are bucketed, never sent raw.
    assert imp["source_scheme"] == "hdfs"
    assert imp["file_format"] == "csv"
    assert imp["outcome"] == "ok"
    assert imp["data_size_bucket"] == "10MB-100MB", imp["data_size_bucket"]
    assert imp["rows_bucket"] == "1K-10K"
    # The raw (un-bucketed) byte count must never appear anywhere in the payload.
    # Exclude non-deterministic fields (ts, session_id) so the check is stable —
    # a random session_id UUID can otherwise contain the substring by chance.
    _raw = str(50 * 1024 * 1024)
    _scrubbed = json.dumps({k: v for k, v in imp.items() if k not in ("ts", "session_id")})
    assert _raw not in _scrubbed, "raw byte size leaked into payload"

    # init carries the build-flavor distribution attribute (h2o vs h2o_client).
    assert by_event["init"].get("attributes", {}).get("distribution"), \
        "init event missing attributes.distribution"

    print("OK telemetry_wire_contract: %d events, envelope + buckets verified" % len(captured))


def telemetry_bucket_boundaries():
    # Spot-check the data-size bucket boundaries (byte-exact label strings).
    cases = [
        (9 * 1024 * 1024,   "<10MB"),
        (10 * 1024 * 1024,  "10MB-100MB"),
        (500 * 1024 * 1024, "500MB-1GB"),
        (5 * 1024 ** 3,     "5GB-10GB"),
    ]
    for size, expected in cases:
        got = t.bucketize_data_size(size)
        assert got == expected, "bucketize_data_size(%d) = %r, expected %r" % (size, got, expected)
    print("OK telemetry_bucket_boundaries: %d boundaries verified" % len(cases))


def telemetry_disabled_emits_nothing():
    captured = []
    orig = t._post_async
    t._post_async = lambda payload, enrich=None: captured.append(payload)
    try:
        t.set_disabled(True)
        t.send_import(VERSION, "s3", "parquet", "ok")
        t.send_init_telemetry(VERSION)
        assert captured == [], "events were emitted while telemetry is disabled: %r" % captured
    finally:
        t._post_async = orig
        t.set_disabled(False)
    print("OK telemetry_disabled_emits_nothing")


def telemetry_http_delivery_smoke():
    received = []

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            n = int(self.headers.get("Content-Length", 0))
            received.append(json.loads(self.rfile.read(n)))
            self.send_response(200)
            self.end_headers()

        def log_message(self, *args):
            pass  # keep the test output clean

    server = HTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    port = server.server_address[1]
    old_url = os.environ.get("H2O_TELEMETRY_URL")
    os.environ["H2O_TELEMETRY_URL"] = "http://127.0.0.1:%d/v1/event" % port
    try:
        t.set_disabled(False)
        t.send_import(VERSION, "local", "csv", "ok", compressed_size_bytes=1024)
        t._telemetry_queue.join()  # block until the worker drains the event
    finally:
        if old_url is None:
            os.environ.pop("H2O_TELEMETRY_URL", None)
        else:
            os.environ["H2O_TELEMETRY_URL"] = old_url
        server.shutdown()

    assert len(received) == 1, "expected exactly one delivered event, got %d" % len(received)
    assert received[0]["event"] == "import"
    assert received[0]["client"] == "python"
    print("OK telemetry_http_delivery_smoke: worker delivered 1 event over HTTP")


if __name__ == "__main__":
    telemetry_wire_contract()
    telemetry_bucket_boundaries()
    telemetry_disabled_emits_nothing()
    telemetry_http_delivery_smoke()
    print("\nALL TELEMETRY TESTS PASSED")
else:
    telemetry_wire_contract()
    telemetry_bucket_boundaries()
    telemetry_disabled_emits_nothing()
    telemetry_http_delivery_smoke()
