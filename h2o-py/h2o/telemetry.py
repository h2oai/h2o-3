# -*- encoding: utf-8 -*-
"""
Telemetry for h2o-py (v1.1).

Sends fire-and-forget HTTPS POSTs to the telemetry receiver describing
client activity: one `init` event at `h2o.init()` plus five activity
event types as the user trains, scores, downloads MOJOs, uploads, and
imports frames. Every request is dispatched on a daemon thread with a
hard 2s timeout, all exceptions are swallowed, and storage on the
server is asynchronous — telemetry must never block, slow down, or
fail any h2o-py call site.

Honors two opt-out environment variables (first match wins):
    H2O_DISABLE_TELEMETRY   -- H2O-specific kill switch
    DO_NOT_TRACK            -- industry-standard opt-out

The receiver URL can be overridden via the `H2O_TELEMETRY_URL`
environment variable. Default points at the local-dev receiver
(127.0.0.1:8000); the production cloud cutover will set the env var to
`https://telemetry.h2o.ai/v1/event`.

See `.planning/h2o-3-client-integration.md` for the wire contract.
"""
import json
import os
import platform
import threading
import time
import urllib.request
import uuid

# v1.1 default points at local-dev receiver; production cloud cutover will
# set H2O_TELEMETRY_URL via the install scripts.
TELEMETRY_URL = "http://127.0.0.1:8000/v1/event"

_PAYLOAD_VERSION = 1
_TIMEOUT_SECONDS = 2.0

# Shared session_id — minted on first send_init_telemetry() call from this
# process, reused for every subsequent event. Reset on the next h2o.init().
_session_lock = threading.Lock()
_session_id = None  # type: str | None


def _resolve_url():
    return os.environ.get("H2O_TELEMETRY_URL") or TELEMETRY_URL


def _normalize_os(name):
    name = (name or "").lower()
    if name == "darwin":
        return "macos"
    return name


def _telemetry_disabled():
    return bool(os.environ.get("H2O_DISABLE_TELEMETRY")) or bool(os.environ.get("DO_NOT_TRACK"))


def _new_session_id():
    with _session_lock:
        global _session_id
        _session_id = str(uuid.uuid4())
        return _session_id


def _current_session_id():
    # Fall back to a fresh UUID if an activity event fires before init —
    # shouldn't happen in practice but keeps activity events well-formed.
    with _session_lock:
        global _session_id
        if _session_id is None:
            _session_id = str(uuid.uuid4())
        return _session_id


# -- Bucketize helpers -- copy verbatim from the v1.1 contract.
# DO NOT rename labels or change boundaries; they must be byte-identical
# across Python / R / Java per the wire contract.

def bucketize_duration_ms(ms):
    if ms < 1_000:          return "<1s"
    if ms < 10_000:         return "1s-10s"
    if ms < 60_000:         return "10s-60s"
    if ms < 600_000:        return "1m-10m"
    if ms < 3_600_000:      return "10m-1h"
    return ">1h"


def bucketize_rows(n):
    if n < 1_000:           return "<1k"
    if n < 10_000:          return "1k-10k"
    if n < 100_000:         return "10k-100k"
    if n < 1_000_000:       return "100k-1M"
    if n < 10_000_000:      return "1M-10M"
    return ">10M"


def bucketize_cols(n):
    if n < 10:              return "<10"
    if n < 100:             return "10-100"
    if n < 1_000:           return "100-1k"
    if n < 10_000:          return "1k-10k"
    return ">10k"


def bucketize_size_bytes(b):
    if b < 1_048_576:                return "<1MB"
    if b < 10 * 1_048_576:           return "1MB-10MB"
    if b < 100 * 1_048_576:          return "10MB-100MB"
    if b < 1_073_741_824:            return "100MB-1GB"
    return ">1GB"


# -- Common envelope shared by all events --

def _envelope(h2o_version):
    return {
        "payload_version": _PAYLOAD_VERSION,
        "client": "python",
        "h2o_version": str(h2o_version) if h2o_version is not None else "",
        "os": _normalize_os(platform.system()),
        "os_version": platform.release() or "",
        "jvm_version": "",
        "session_id": _current_session_id(),
        "ts": int(time.time()),
    }


def _post_async(payload):
    if _telemetry_disabled():
        return
    url = _resolve_url()

    def _post():
        try:
            req = urllib.request.Request(
                url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            urllib.request.urlopen(req, timeout=_TIMEOUT_SECONDS).read()
        except Exception:
            pass

    threading.Thread(target=_post, daemon=True).start()


# -- Public emitters -- one per event type.

def send_init_telemetry(h2o_version):
    """Fire one `event=init` POST. Mints a fresh session_id for this process."""
    if _telemetry_disabled():
        return
    _new_session_id()
    payload = {**_envelope(h2o_version), "event": "init"}
    _post_async(payload)


def send_algo_train(h2o_version, algo, family, outcome,
                    duration_ms, n_rows, n_cols, n_models=None):
    """Fire `event=algo_train` after a training call reaches a terminal state."""
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "algo_train",
        "algo": algo,
        "family": family,
        "outcome": outcome,
        "duration_ms_bucket": bucketize_duration_ms(duration_ms),
        "rows_bucket": bucketize_rows(n_rows),
        "cols_bucket": bucketize_cols(n_cols),
        "n_models": n_models,
    }
    _post_async(payload)


def send_algo_score(h2o_version, algo, family, outcome, duration_ms, n_rows):
    """Fire `event=algo_score` after a scoring call reaches a terminal state."""
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "algo_score",
        "algo": algo,
        "family": family,
        "outcome": outcome,
        "rows_bucket": bucketize_rows(n_rows),
        "duration_ms_bucket": bucketize_duration_ms(duration_ms),
    }
    _post_async(payload)


def send_mojo_download(h2o_version, algo, family, outcome, compressed_size_bytes):
    """Fire `event=mojo_download` after a MOJO archive is written to disk."""
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "mojo_download",
        "algo": algo,
        "family": family,
        "outcome": outcome,
        "compressed_size_bucket": bucketize_size_bytes(compressed_size_bytes),
    }
    _post_async(payload)


def send_upload(h2o_version, file_format, compressed_size_bytes, outcome):
    """Fire `event=upload` after a local-file upload completes."""
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "upload",
        "file_format": file_format,
        "compressed_size_bucket": bucketize_size_bytes(compressed_size_bytes),
        "outcome": outcome,
    }
    _post_async(payload)


def send_import(h2o_version, source_scheme, file_format, outcome):
    """Fire `event=import` after a remote-file import completes."""
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "import",
        "source_scheme": source_scheme,
        "file_format": file_format,
        "outcome": outcome,
    }
    _post_async(payload)


# -- Derivation helpers for call-site wiring --

_SOURCE_SCHEME_MAP = {
    "s3": "s3",
    "s3a": "s3",
    "s3n": "s3",
    "hdfs": "hdfs",
    "gs": "gcs",
    "gcs": "gcs",
    "http": "http",
    "https": "http",
    "file": "local",
}

# Order matters: longer / more-specific extensions first.
_FILE_FORMAT_MAP = (
    (".parquet", "parquet"),
    (".orc",     "orc"),
    (".arff",    "arff"),
    (".csv",     "csv"),
    (".tsv",     "csv"),
    (".gz",      "other"),
    (".zip",     "other"),
)


def derive_source_scheme(path):
    """Map a URL/path to one of: s3 / hdfs / gcs / http / local / other."""
    if not path:
        return "local"
    s = str(path).strip().lower()
    if "://" in s:
        scheme = s.split("://", 1)[0]
        return _SOURCE_SCHEME_MAP.get(scheme, "other")
    return "local"


def derive_file_format(path_or_name):
    """Map a filename/extension to one of: csv / parquet / orc / arff / other."""
    if not path_or_name:
        return "other"
    s = str(path_or_name).strip().lower()
    for ext, label in _FILE_FORMAT_MAP:
        if s.endswith(ext):
            return label
    return "other"
