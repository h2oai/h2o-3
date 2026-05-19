# -*- encoding: utf-8 -*-
"""
Telemetry for h2o-py (v1.5).

Sends fire-and-forget HTTPS POSTs to the telemetry receiver describing
client activity. Every request runs on a daemon thread with a hard 2s
timeout, all exceptions are swallowed — telemetry must never block,
slow down, or fail any h2o-py call site. An unreachable server is
invisible to the user.

Supported event types:
    init             one per h2o.init() that spawned a local server
    cluster_connect  one per h2o.init() / h2o.connect() that attached to an existing cluster
    algo_train       one per estimator.train()
    algo_score       one per model.predict()
    mojo_download    one per model.download_mojo()
    upload           one per h2o.upload_file()
    import           one per h2o.import_file()
    automl_run       one per H2OAutoML.train()
    model_save       one per h2o.save_model()
    model_load       one per h2o.load_model()

Honors two opt-out environment variables (first match wins):
    H2O_DISABLE_TELEMETRY   -- H2O-specific kill switch
    DO_NOT_TRACK            -- industry-standard opt-out

URL override: H2O_TELEMETRY_URL.

See `.planning/h2o-3-client-integration.md` and
`.planning/h2o-3-update-v1.3-v1.4.md` for the wire contract.
"""
import json
import os
import platform
import re
import subprocess
import threading
import time
import urllib.request
import uuid

# v1.1 default points at local-dev receiver; production cloud cutover
# will set H2O_TELEMETRY_URL via the install scripts.
TELEMETRY_URL = "http://127.0.0.1:8000/v1/event"

_PAYLOAD_VERSION = 1
_TIMEOUT_SECONDS = 2.0
_MAX_VERSION_FIELD_LEN = 64  # v1.5 Phase 24 — cap every *_version / *_vendor field

# Shared session_id — minted on first init/cluster_connect call from this
# process, reused for every subsequent event. Reset on the next h2o.init().
_session_lock = threading.Lock()
_session_id = None  # type: str | None

# Programmatic opt-out set by h2o.init(telemetry=False). Independent of and
# additive to the env-var opt-outs. Persists for the lifetime of the process
# (or until set_disabled(False) is called).
_disabled_by_kwarg = False

# Cached Java runtime info (avoid re-parsing `java -version` on every send).
_java_info_cache = None  # type: dict | None
_java_info_lock = threading.Lock()


def _resolve_url():
    return os.environ.get("H2O_TELEMETRY_URL") or TELEMETRY_URL


def _normalize_os(name):
    name = (name or "").lower()
    if name == "darwin":
        return "macos"
    return name


def set_disabled(disabled):
    """Programmatic opt-out, set by ``h2o.init(telemetry=False)``.

    Once disabled, every subsequent ``send_*`` call is a no-op until the
    next process restart. Independent of and additive to the env-var
    opt-outs (``H2O_DISABLE_TELEMETRY`` / ``DO_NOT_TRACK``).
    """
    global _disabled_by_kwarg
    _disabled_by_kwarg = bool(disabled)


def _telemetry_disabled():
    if _disabled_by_kwarg:
        return True
    return bool(os.environ.get("H2O_DISABLE_TELEMETRY")) or bool(os.environ.get("DO_NOT_TRACK"))


def _new_session_id():
    with _session_lock:
        global _session_id
        _session_id = str(uuid.uuid4())
        return _session_id


def _current_session_id():
    with _session_lock:
        global _session_id
        if _session_id is None:
            _session_id = str(uuid.uuid4())
        return _session_id


def _cap_version(value, max_len=_MAX_VERSION_FIELD_LEN):
    """Truncate a version-ish string to max_len; pass through None unchanged."""
    if value is None:
        return None
    s = str(value)
    return s[:max_len] if len(s) > max_len else s


# -- Bucketize helpers -- byte-identical labels across Python / R / Java per the wire contract.

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
    # Decimal MB/GB per v1.5 contract — labels are "MB" / "GB", not "MiB".
    if b < 1_000_000:        return "<1MB"
    if b < 10_000_000:       return "1MB-10MB"
    if b < 100_000_000:      return "10MB-100MB"
    if b < 1_000_000_000:    return "100MB-1GB"
    return ">1GB"


# -- v1.4 / v1.5 bucket helpers --

def bucketize_cluster_nodes(n):
    if n == 1:    return "1"
    if n <= 4:    return "2-4"
    if n <= 16:   return "5-16"
    if n <= 64:   return "17-64"
    return ">64"


def bucketize_cluster_memory_gb(gb):
    if gb < 1:    return "<1"
    if gb < 8:    return "1-8"
    if gb < 32:   return "8-32"
    if gb < 128:  return "32-128"
    if gb < 512:  return "128-512"
    return ">512"


# `frame_memory_gb_bucket` shares boundaries with cluster_memory_gb_bucket per v1.5 spec.
bucketize_frame_memory_gb = bucketize_cluster_memory_gb


def bucketize_max_models(n):
    if n < 10:    return "<10"
    if n < 50:    return "10-50"
    if n < 200:   return "50-200"
    return ">200"


def bucketize_max_runtime_secs(secs):
    if secs < 60:        return "<60s"
    if secs < 600:       return "60s-10m"
    if secs < 3600:      return "10m-1h"
    return ">1h"


def bucketize_leaderboard_size(n):
    if n < 10:    return "<10"
    if n < 50:    return "10-50"
    if n < 100:   return "50-100"
    return ">100"


# -- Runtime version detection --

_JAVA_VERSION_RE = re.compile(r'version\s+"([^"]+)"')
_JAVA_PROP_RE   = re.compile(r'^\s*(java\.version|java\.vendor)\s*=\s*(.+?)\s*$', re.MULTILINE)


def _detect_java_info():
    """Best-effort detection of Java version + vendor by parsing `java -version`.

    Returns ``{"version": str, "vendor": str}`` or empty dict if Java is
    unavailable. Cached after first call. Never raises.
    """
    global _java_info_cache
    with _java_info_lock:
        if _java_info_cache is not None:
            return _java_info_cache
        info = {}
        try:
            proc = subprocess.run(
                ["java", "-XshowSettings:properties", "-version"],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                timeout=2.0,
            )
            text = (proc.stderr or b"").decode("utf-8", "replace") + "\n" + \
                   (proc.stdout or b"").decode("utf-8", "replace")
            for m in _JAVA_PROP_RE.finditer(text):
                if m.group(1) == "java.version":
                    info["version"] = m.group(2)
                elif m.group(1) == "java.vendor":
                    info["vendor"] = m.group(2)
            if "version" not in info:
                m = _JAVA_VERSION_RE.search(text)
                if m:
                    info["version"] = m.group(1)
        except Exception:
            pass
        _java_info_cache = info
        return info


def _python_version_safe():
    try:
        return _cap_version(platform.python_version())
    except Exception:
        return None


def _java_version_safe():
    info = _detect_java_info()
    return _cap_version(info.get("version")) if info.get("version") else None


def _java_vendor_safe():
    info = _detect_java_info()
    return _cap_version(info.get("vendor")) if info.get("vendor") else None


# -- Cluster-shape detection --

_KUBERNETES_ENV_HINTS = ("KUBERNETES_SERVICE_HOST",)
_HADOOP_ENV_HINTS = ("HADOOP_HOME", "HADOOP_CONF_DIR", "HADOOP_PREFIX")


def _derive_cluster_topology(cloud_size, hadoop_version=None):
    """Derive the cluster_topology enum per v1.4 Phase 19 rules.

    `hadoop_version` non-empty implies a Hadoop deployment. Otherwise we
    fall back to env-var sniffing for Kubernetes / Hadoop signals.
    """
    try:
        n = int(cloud_size or 0)
    except Exception:
        return "unknown"
    if n == 1:
        return "single_node"
    if hadoop_version:
        return "multi_node_hadoop"
    if any(os.environ.get(k) for k in _KUBERNETES_ENV_HINTS):
        return "kubernetes"
    if any(os.environ.get(k) for k in _HADOOP_ENV_HINTS):
        return "multi_node_hadoop"
    if n > 1:
        return "multi_node_standalone"
    return "unknown"


def derive_cluster_shape(h2oconn):
    """Read cluster shape from an h2oconn object. Best-effort, never raises.

    Returns a dict with keys ``cluster_nodes_bucket``, ``cluster_memory_gb_bucket``,
    ``cluster_topology`` — any value may be None if unavailable.
    """
    out = {"cluster_nodes_bucket": None, "cluster_memory_gb_bucket": None, "cluster_topology": None}
    try:
        cluster = getattr(h2oconn, "cluster", None)
        if cluster is None:
            return out
        cloud_size = int(getattr(cluster, "cloud_size", 0) or 0)
        if cloud_size > 0:
            out["cluster_nodes_bucket"] = bucketize_cluster_nodes(cloud_size)
        nodes = getattr(cluster, "nodes", None) or []
        if nodes:
            total_bytes = 0
            for n in nodes:
                # nodes are dicts; max_mem is the per-node JVM heap ceiling
                try:
                    total_bytes += int(n.get("max_mem") or 0)
                except Exception:
                    pass
            if total_bytes > 0:
                out["cluster_memory_gb_bucket"] = bucketize_cluster_memory_gb(total_bytes / (1024 ** 3))
        out["cluster_topology"] = _derive_cluster_topology(cloud_size)
    except Exception:
        pass
    return out


# -- Common envelope shared by all events --

def _envelope(h2o_version):
    return {
        "payload_version": _PAYLOAD_VERSION,
        "client": "python",
        "h2o_version": str(h2o_version) if h2o_version is not None else "",
        "os": _normalize_os(platform.system()),
        "os_version": platform.release() or "",
        "jvm_version": _java_version_safe() or "",
        "session_id": _current_session_id(),
        "ts": int(time.time()),
    }


def _attach_extras(payload, attributes=None):
    """Add the nullable v1.3+ `attributes` field if any keys were supplied."""
    if attributes:
        # Coerce all values to strings per the v1.3 attribute contract.
        payload["attributes"] = {str(k): str(v) for k, v in attributes.items() if v is not None}
    return payload


def _post_async(payload):
    if _telemetry_disabled():
        return
    url = _resolve_url()

    def _post():
        try:
            req = urllib.request.Request(
                url,
                data=json.dumps(payload).encode("utf-8"),
                # DNT: 0 explicitly signals "user has not opted out via the
                # W3C DNT mechanism" — defends against transparent proxies
                # that inject DNT: 1 on outbound traffic and would otherwise
                # silently kill our telemetry. Users opt out via env vars,
                # never by us sending DNT: 1.
                headers={"Content-Type": "application/json", "DNT": "0"},
                method="POST",
            )
            urllib.request.urlopen(req, timeout=_TIMEOUT_SECONDS).read()
        except Exception:
            pass

    threading.Thread(target=_post, daemon=True).start()


def _strip_none(d):
    """Drop None values from a dict (receiver treats omitted == null)."""
    return {k: v for k, v in d.items() if v is not None}


# -- Public emitters ----------------------------------------------------------

def send_init_telemetry(h2o_version, *, cluster_shape=None, attributes=None):
    """Fire one `event=init` POST (local-server-spawn branch).

    Mints a fresh session_id for this process. ``cluster_shape`` is the
    dict returned by :func:`derive_cluster_shape` — pass it in when known.
    """
    if _telemetry_disabled():
        return
    _new_session_id()
    payload = {**_envelope(h2o_version), "event": "init"}
    payload.update(_strip_none({
        "python_version": _python_version_safe(),
        "java_version":   _java_version_safe(),
        "java_vendor":    _java_vendor_safe(),
    }))
    if cluster_shape:
        payload.update(_strip_none(cluster_shape))
    _attach_extras(payload, attributes)
    _post_async(payload)


def send_cluster_connect_telemetry(h2o_version, *, cluster_shape=None, attributes=None):
    """Fire one `event=cluster_connect` POST (connect-only branch — no local server spawned).

    Same envelope and runtime/cluster-shape fields as ``init``; mints a fresh
    session_id (a connect *is* a new session, just one that didn't start the JVM).
    """
    if _telemetry_disabled():
        return
    _new_session_id()
    payload = {**_envelope(h2o_version), "event": "cluster_connect"}
    payload.update(_strip_none({
        "python_version": _python_version_safe(),
        "java_version":   _java_version_safe(),
        "java_vendor":    _java_vendor_safe(),
    }))
    if cluster_shape:
        payload.update(_strip_none(cluster_shape))
    _attach_extras(payload, attributes)
    _post_async(payload)


def send_algo_train(h2o_version, algo, family, outcome,
                    duration_ms, n_rows, n_cols, n_models=None, attributes=None):
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
    _attach_extras(payload, attributes)
    _post_async(payload)


def send_algo_score(h2o_version, algo, family, outcome, duration_ms, n_rows, attributes=None):
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
    _attach_extras(payload, attributes)
    _post_async(payload)


def send_mojo_download(h2o_version, algo, family, outcome, compressed_size_bytes, attributes=None):
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
    _attach_extras(payload, attributes)
    _post_async(payload)


def send_upload(h2o_version, file_format, compressed_size_bytes, outcome,
                *, frame_shape=None, attributes=None):
    """Fire one `event=upload` POST.

    ``frame_shape`` is an optional dict with keys ``rows_bucket``, ``cols_bucket``,
    ``frame_memory_gb_bucket`` (v1.5 Phase 23). Pass ``None`` (or omit) when
    the parse failed — sending bucket values for an error path is misleading.
    """
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "upload",
        "file_format": file_format,
        "compressed_size_bucket": bucketize_size_bytes(compressed_size_bytes),
        "outcome": outcome,
    }
    if frame_shape:
        payload.update(_strip_none(frame_shape))
    _attach_extras(payload, attributes)
    _post_async(payload)


def send_import(h2o_version, source_scheme, file_format, outcome,
                *, compressed_size_bytes=None, frame_shape=None, attributes=None):
    """Fire one `event=import` POST.

    v1.5 Phase 23 adds optional ``compressed_size_bytes`` (the size of the
    remote payload) and ``frame_shape`` (post-parse). Both omitted on error
    paths or when the size isn't cheap to derive.
    """
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "import",
        "source_scheme": source_scheme,
        "file_format": file_format,
        "outcome": outcome,
    }
    if compressed_size_bytes is not None:
        payload["compressed_size_bucket"] = bucketize_size_bytes(compressed_size_bytes)
    if frame_shape:
        payload.update(_strip_none(frame_shape))
    _attach_extras(payload, attributes)
    _post_async(payload)


def send_automl_run(h2o_version, algo, family, outcome,
                    max_models, max_runtime_secs, sort_metric, leaderboard_size,
                    attributes=None):
    """Fire one `event=automl_run` per H2OAutoML.train() call.

    ``algo`` is the **leader-model** algo (never the literal string
    ``"automl"``). Any numeric input may be None → corresponding bucket
    field is set to None.
    """
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "automl_run",
        "algo": algo,
        "family": family,
        "outcome": outcome,
        "max_models_bucket":      bucketize_max_models(int(max_models)) if max_models is not None else None,
        "max_runtime_secs_bucket": bucketize_max_runtime_secs(float(max_runtime_secs)) if max_runtime_secs is not None else None,
        "sort_metric":            sort_metric,
        "leaderboard_size_bucket": bucketize_leaderboard_size(int(leaderboard_size)) if leaderboard_size is not None else None,
    }
    payload = _strip_none(payload)
    _attach_extras(payload, attributes)
    _post_async(payload)


def send_model_save(h2o_version, algo, family, outcome, fmt, compressed_size_bytes, attributes=None):
    """Fire one `event=model_save` per h2o.save_model() / .download_mojo()-via-save call.

    ``fmt`` is one of ``"binary" | "mojo" | "pojo"``.
    """
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "model_save",
        "algo": algo,
        "family": family,
        "outcome": outcome,
        "format": fmt,
        "compressed_size_bucket": bucketize_size_bytes(compressed_size_bytes) if compressed_size_bytes is not None else None,
    }
    payload = _strip_none(payload)
    _attach_extras(payload, attributes)
    _post_async(payload)


def send_model_load(h2o_version, algo, family, outcome, fmt, compressed_size_bytes, attributes=None):
    """Fire one `event=model_load` per h2o.load_model() / load_mojo / load_grid call."""
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "model_load",
        "algo": algo,
        "family": family,
        "outcome": outcome,
        "format": fmt,
        "compressed_size_bucket": bucketize_size_bytes(compressed_size_bytes) if compressed_size_bytes is not None else None,
    }
    payload = _strip_none(payload)
    _attach_extras(payload, attributes)
    _post_async(payload)


# -- Derivation helpers (path / scheme / format) -----------------------------

_SOURCE_SCHEME_MAP = {
    "s3": "s3", "s3a": "s3", "s3n": "s3",
    "hdfs": "hdfs",
    "gs": "gcs", "gcs": "gcs",
    "http": "http", "https": "http",
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


def derive_frame_shape(frame):
    """Return v1.5 bucket fields for a parsed H2OFrame, or empty dict on failure."""
    if frame is None:
        return {}
    out = {}
    try:
        out["rows_bucket"] = bucketize_rows(int(frame.nrow or 0))
        out["cols_bucket"] = bucketize_cols(int(frame.ncol or 0))
    except Exception:
        pass
    try:
        b = int(getattr(frame, "byte_size", 0) or 0)
        if b > 0:
            out["frame_memory_gb_bucket"] = bucketize_frame_memory_gb(b / (1024 ** 3))
    except Exception:
        pass
    return out
