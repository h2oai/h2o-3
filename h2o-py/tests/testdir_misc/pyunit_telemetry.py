import sys
sys.path.insert(1, "../../")
import io
import json
import os
import tempfile
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


def telemetry_first_run_notice():
    # Use a throwaway HOME so the per-environment marker doesn't touch the real one.
    old_home = os.environ.get("HOME")
    tmp = tempfile.mkdtemp()
    os.environ["HOME"] = tmp

    def run():
        err = io.StringIO()
        old = sys.stderr
        sys.stderr = err
        try:
            t._maybe_print_notice()
        finally:
            sys.stderr = old
        return err.getvalue()

    try:
        t.set_disabled(False)
        first, second = run(), run()
        assert "anonymous usage telemetry" in first, "notice not printed on first run"
        assert second == "", "notice repeated on second run (marker not honored)"
        assert os.path.exists(os.path.join(tmp, ".h2oai", ".telemetry_notice_python"))

        # Disabled telemetry never prints, even with no marker.
        os.remove(os.path.join(tmp, ".h2oai", ".telemetry_notice_python"))
        t.set_disabled(True)
        assert run() == "", "notice printed while telemetry disabled"
    finally:
        t.set_disabled(False)
        if old_home is None:
            os.environ.pop("HOME", None)
        else:
            os.environ["HOME"] = old_home
    print("OK telemetry_first_run_notice: shown once, suppressed when disabled")


def telemetry_do_not_track_truthiness():
    # DO_NOT_TRACK reacts to 1/0/true/false; only truthy values opt out, and it
    # always wins over the programmatic flag.
    old = os.environ.get("DO_NOT_TRACK")
    try:
        t.set_disabled(False)
        for val, disabled in [("1", True), ("true", True), ("on", True),
                              ("0", False), ("false", False), ("", False)]:
            os.environ["DO_NOT_TRACK"] = val
            assert t._telemetry_disabled() is disabled, \
                "DO_NOT_TRACK=%r -> disabled=%r" % (val, t._telemetry_disabled())
        os.environ.pop("DO_NOT_TRACK", None)
        assert t._telemetry_disabled() is False, "unset DO_NOT_TRACK must not opt out"
    finally:
        if old is None:
            os.environ.pop("DO_NOT_TRACK", None)
        else:
            os.environ["DO_NOT_TRACK"] = old
    print("OK telemetry_do_not_track_truthiness: 1/true opt out, 0/false/empty do not")


def telemetry_set_persisted_pref():
    # set_telemetry persists the choice under ~/.h2oai and is reloaded next process.
    old_home = os.environ.get("HOME")
    old_dnt = os.environ.get("DO_NOT_TRACK")
    tmp = tempfile.mkdtemp()
    os.environ["HOME"] = tmp
    os.environ.pop("DO_NOT_TRACK", None)
    pref = os.path.join(tmp, ".h2oai", "telemetry")
    try:
        assert t.set_telemetry(False) is True, "set_telemetry(False) did not persist"
        assert os.path.exists(pref), "preference file not written"
        with open(pref) as f:
            assert f.read().strip() == "0"
        assert t.telemetry_enabled() is False

        assert t.set_telemetry(True) is True
        with open(pref) as f:
            assert f.read().strip() == "1"
        assert t.telemetry_enabled() is True

        # A fresh process reloads the saved choice at import.
        t.set_disabled(False)
        with open(pref, "w") as f:
            f.write("0")
        t._load_persisted_pref()
        assert t._telemetry_disabled() is True, "persisted opt-out not reloaded"
    finally:
        t.set_disabled(False)
        if old_home is None:
            os.environ.pop("HOME", None)
        else:
            os.environ["HOME"] = old_home
        if old_dnt is not None:
            os.environ["DO_NOT_TRACK"] = old_dnt
    print("OK telemetry_set_persisted_pref: set_telemetry writes ~/.h2oai and reloads")


def telemetry_config_file_opt_out():
    # ~/.h2oconfig (home) can switch telemetry off; any opt-out wins (union).
    old_home = os.environ.get("HOME")
    old_dnt = os.environ.get("DO_NOT_TRACK")
    tmp = tempfile.mkdtemp()
    os.environ["HOME"] = tmp
    os.environ.pop("DO_NOT_TRACK", None)
    cfg = os.path.join(tmp, ".h2oconfig")
    try:
        with open(cfg, "w") as f:
            f.write("[general]\ntelemetry = false\n")
        t.set_disabled(False)
        t._load_persisted_pref()
        assert t._telemetry_disabled() is True, "config telemetry=false did not opt out"

        with open(cfg, "w") as f:
            f.write("general.telemetry = true\n")
        t.set_disabled(True)
        t._load_persisted_pref()
        assert t._telemetry_disabled() is False, "config telemetry=true did not opt in"

        # An opt-out wins even if ~/.h2oai/telemetry says on.
        os.makedirs(os.path.join(tmp, ".h2oai"), exist_ok=True)
        with open(os.path.join(tmp, ".h2oai", "telemetry"), "w") as f:
            f.write("1")
        with open(cfg, "w") as f:
            f.write("[general]\ntelemetry = off\n")
        t.set_disabled(False)
        t._load_persisted_pref()
        assert t._telemetry_disabled() is True, "config opt-out should win (union off)"
    finally:
        t.set_disabled(False)
        if old_home is None:
            os.environ.pop("HOME", None)
        else:
            os.environ["HOME"] = old_home
        if old_dnt is not None:
            os.environ["DO_NOT_TRACK"] = old_dnt
    print("OK telemetry_config_file_opt_out: ~/.h2oconfig telemetry key honored (home-only, union-off)")


if __name__ == "__main__":
    telemetry_wire_contract()
    telemetry_bucket_boundaries()
    telemetry_disabled_emits_nothing()
    telemetry_do_not_track_truthiness()
    telemetry_first_run_notice()
    telemetry_http_delivery_smoke()
    telemetry_set_persisted_pref()
    telemetry_config_file_opt_out()
    print("\nALL TELEMETRY TESTS PASSED")
else:
    telemetry_wire_contract()
    telemetry_bucket_boundaries()
    telemetry_disabled_emits_nothing()
    telemetry_do_not_track_truthiness()
    telemetry_first_run_notice()
    telemetry_http_delivery_smoke()
    telemetry_set_persisted_pref()
    telemetry_config_file_opt_out()
