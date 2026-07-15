# -*- encoding: utf-8 -*-
"""
Anonymous usage telemetry for h2o-py.

Sends fire-and-forget HTTPS POSTs to the telemetry endpoint describing
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
    model_download   one per h2o.download_pojo() / h2o.download_model() (non-MOJO)
    upload           one per h2o.upload_file()
    import           one per h2o.import_file()
    frame_parsed     one per completed parse (alongside upload / import)
    automl_run       one per H2OAutoML.train()
    model_save       one per h2o.save_model()
    model_load       one per h2o.load_model()

Honors the DO_NOT_TRACK opt-out environment variable (consoledonottrack.com),
which always wins. Reacts to 1/0/true/false; 0/false/empty do not opt out.

URL override: H2O_TELEMETRY_URL.
"""
import functools
import json
import os
import platform
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
import uuid

# Product attribution is hardcoded per repo at build time (OSS vs Enterprise).
# Guard the import so a missing/edited _product.py can never break `import h2o`.
try:
    from h2o._product import _PRODUCT
except Exception:
    _PRODUCT = "h2o-3-oss"

# Distribution marker: which PyPI package this is — "h2o" (full) vs "h2o_client"
# (client-only). Baked per build flavor by createVersionFiles (h2o-py/build.gradle);
# absent in a source checkout, so default to "h2o" (the full package). Reported as
# attributes.distribution on the session-start events. Distinct from _PRODUCT
# (build flavor) and python_distribution (pip/conda installer).
try:
    from h2o._distribution import _DISTRIBUTION
except Exception:
    _DISTRIBUTION = "h2o"

# Production endpoint. Override via the H2O_TELEMETRY_URL environment variable.
TELEMETRY_URL = "https://telemetry.h2o.ai/v1/event"

_PAYLOAD_VERSION = 1
_TIMEOUT_SECONDS = 2.0
_MAX_VERSION_FIELD_LEN = 64  # cap every *_version / *_vendor field

# Shared session_id — minted on first init/cluster_connect call from this
# process, reused for every subsequent event. Reset on the next h2o.init().
_session_lock = threading.Lock()
_session_id = None  # type: str | None

# Programmatic opt-out set by h2o.init(telemetry=False). Independent of and
# additive to the env-var opt-outs. Persists for the lifetime of the process
# (or until set_disabled(False) is called).
#
# Its initial value is the client-wide default when the user never passes the
# telemetry kwarg: True = telemetry off (opt-in model) — a bare
# h2o.init()/h2o.connect() stays off until the user opts in (telemetry=True,
# h2o.set_telemetry(True), or a persisted/config opt-in). Flip to False for the
# opt-out model (on by default unless the user opts out).
_disabled_by_kwarg = True

# Client state persisted under the user's home config dir. The full path is
# resolved per call (see _config_dir) so a changed HOME / test sandbox is honored.
_TELEMETRY_PREF_FILE = "telemetry"                 # contents: "1" = on, "0" = off
_NOTICE_MARKER_FILE = ".telemetry_notice_python"   # contents: notice version last seen
_NOTICE_VERSION = 1                                # bump ONLY when the notice/policy changes

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
    next process restart. Independent of the ``DO_NOT_TRACK`` env-var opt-out,
    which always wins.
    """
    global _disabled_by_kwarg
    _disabled_by_kwarg = bool(disabled)


def _env_truthy(name):
    """True iff env var ``name`` is set to a truthy value.

    Reacts to 1/0/true/false/yes/no/on/off (case-insensitive). ``0`` / ``false``
    / empty / unset all read as False, so e.g. ``DO_NOT_TRACK=0`` does NOT opt out.
    """
    v = os.environ.get(name)
    if v is None:
        return False
    return v.strip().lower() not in ("", "0", "false", "no", "off")


def _telemetry_disabled():
    # DO_NOT_TRACK (cross-tool standard — consoledonottrack.com) is the hard
    # opt-out and always wins, including over a programmatic telemetry=True.
    if _env_truthy("DO_NOT_TRACK"):
        return True
    return _disabled_by_kwarg


def _config_dir():
    """User-level H2O client config dir (``~/.h2oai``). Resolved per call so a
    changed HOME (e.g. a test sandbox) is always honored."""
    return os.path.join(os.path.expanduser("~"), ".h2oai")


def telemetry_enabled():
    """Return whether anonymous client telemetry is currently enabled."""
    return not _telemetry_disabled()


def set_telemetry(enabled):
    """Enable/disable client telemetry and remember the choice across sessions.

    Applies immediately for this process and is persisted under ``~/.h2oai`` so
    later sessions honor it. ``DO_NOT_TRACK`` still overrides it. Best-effort and
    silent: never raises, never prints.

    :returns: ``True`` if the preference was written to disk, else ``False``.
    """
    set_disabled(not enabled)
    try:
        d = _config_dir()
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, _TELEMETRY_PREF_FILE), "w") as f:
            f.write("1" if enabled else "0")
        return True
    except Exception:
        return False


def _parse_bool(s):
    """Parse a persisted/config flag: True, False, or None if unrecognized."""
    s = (s or "").strip().lower()
    if s in ("1", "true", "yes", "on"):
        return True
    if s in ("0", "false", "no", "off"):
        return False
    return None


def _file_telemetry_pref():
    """On/off from ``~/.h2oai/telemetry`` (written by set_telemetry). None if unset."""
    try:
        with open(os.path.join(_config_dir(), _TELEMETRY_PREF_FILE)) as f:
            return _parse_bool(f.read())
    except Exception:
        return None


def _config_telemetry_pref():
    """On/off from ``~/.h2oconfig`` (home only). None if unset/unreadable.

    Recognizes ``telemetry = <bool>`` under a ``[general]`` section (or no
    section), or ``general.telemetry = <bool>``. Home-only by design: a privacy
    opt-out must not depend on the working directory (unlike the connection keys
    that H2OConfigReader walks up from cwd)."""
    try:
        section = None
        found = None
        with open(os.path.join(os.path.expanduser("~"), ".h2oconfig")) as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                if line.startswith("[") and line.endswith("]"):
                    section = line[1:-1].strip().lower()
                    continue
                if "=" not in line:
                    continue
                key, _, raw = line.partition("=")
                key = key.strip().lower()
                if ":" in key:                       # strip a py:/r: language prefix
                    prefix, _, key = key.partition(":")
                    if prefix not in ("py", "python"):
                        continue
                if key == "general.telemetry" or (key == "telemetry" and section in (None, "general")):
                    found = _parse_bool(raw)
        return found
    except Exception:
        return None


def _load_persisted_pref():
    """Seed the initial opt-out state at import from the static opt-out surfaces:
    ``~/.h2oconfig`` (home) and ``~/.h2oai/telemetry``. Any explicit opt-out wins
    (union — consistent with DO_NOT_TRACK); otherwise an explicit opt-in applies;
    otherwise the default is kept. All best-effort."""
    global _disabled_by_kwarg
    prefs = [_config_telemetry_pref(), _file_telemetry_pref()]
    if any(p is False for p in prefs):
        _disabled_by_kwarg = True
    elif any(p is True for p in prefs):
        _disabled_by_kwarg = False


_load_persisted_pref()


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


# -- Bucketize helpers. The label strings must stay byte-identical across the
# -- Python / R / JVM clients, since they are compared as a fixed set server-side.

def bucketize_duration_ms(ms):
    # Sub-second values floor to "<5s" — no sub-second resolution.
    seconds = ms / 1000.0
    if seconds < 5:         return "<5s"
    if seconds < 15:        return "5s-15s"
    if seconds < 30:        return "15s-30s"
    if seconds < 60:        return "30s-1m"
    if seconds < 120:       return "1m-2m"
    if seconds < 300:       return "2m-5m"
    if seconds < 600:       return "5m-10m"
    if seconds < 900:       return "10m-15m"
    if seconds < 1_800:     return "15m-30m"
    if seconds < 3_600:     return "30m-1h"
    if seconds < 7_200:     return "1h-2h"
    if seconds < 14_400:    return "2h-4h"
    if seconds < 21_600:    return "4h-6h"
    return ">6h"


def bucketize_rows(n):
    if n < 1_000:           return "<1k"
    if n < 3_000:           return "1k-3k"
    if n < 10_000:          return "3k-10k"
    if n < 30_000:          return "10k-30k"
    if n < 100_000:         return "30k-100k"
    if n < 300_000:         return "100k-300k"
    if n < 1_000_000:       return "300k-1M"
    if n < 3_000_000:       return "1M-3M"
    if n < 10_000_000:      return "3M-10M"
    if n < 30_000_000:      return "10M-30M"
    if n < 100_000_000:     return "30M-100M"
    return ">100M"


def bucketize_cols(n):
    if n < 10:              return "<10"
    if n < 30:              return "10-30"
    if n < 100:             return "30-100"
    if n < 300:             return "100-300"
    if n < 1_000:           return "300-1k"
    if n < 3_000:           return "1k-3k"
    if n < 10_000:          return "3k-10k"
    if n < 30_000:          return "10k-30k"
    if n < 100_000:         return "30k-100k"
    if n < 300_000:         return "100k-300k"
    if n < 1_000_000:       return "300k-1M"
    return ">1M"


# Three range-tuned size scales (MOJO, model artifact, dataset). All use MiB (1_048_576).

def bucketize_mojo_size(b):
    # MOJOs are typically 1-50 MB; the 100KB floor catches tiny GLM MOJOs.
    mb = b / 1_048_576.0
    if mb < 0.1:    return "<100KB"
    if mb < 1:      return "100KB-1MB"
    if mb < 5:      return "1MB-5MB"
    if mb < 10:     return "5MB-10MB"
    if mb < 50:     return "10MB-50MB"
    if mb < 100:    return "50MB-100MB"
    return ">100MB"


def bucketize_artifact_size(b):
    # Binary / POJO models; deep-stacked ensembles can exceed 1 GB.
    mb = b / 1_048_576.0
    if mb < 0.1:    return "<100KB"
    if mb < 1:      return "100KB-1MB"
    if mb < 10:     return "1MB-10MB"
    if mb < 100:    return "10MB-100MB"
    if mb < 1024:   return "100MB-1GB"
    return ">1GB"


def bucketize_data_size(b):
    # Uploaded / imported datasets. Spans MB to multi-TB.
    mb = b / 1_048_576.0
    gb = mb / 1024.0
    if mb < 10:     return "<10MB"
    if mb < 100:    return "10MB-100MB"
    if mb < 500:    return "100MB-500MB"
    if mb < 1024:   return "500MB-1GB"
    if gb < 5:      return "1GB-5GB"
    if gb < 10:     return "5GB-10GB"
    if gb < 50:     return "10GB-50GB"
    if gb < 100:    return "50GB-100GB"
    if gb < 250:    return "100GB-250GB"
    if gb < 500:    return "250GB-500GB"
    if gb < 1024:   return "500GB-1TB"
    if gb < 1536:   return "1TB-1.5TB"
    if gb < 2048:   return "1.5TB-2TB"
    return ">2TB"


# -- cluster-shape bucket helpers --

def bucketize_cluster_nodes(n):
    # Capture fine, display coarse: exact node count for n <= 16 (1-node vs
    # 4-node is operationally meaningful), doubling-ish buckets above 16.
    if n <= 16:   return str(n)
    if n <= 20:   return "17-20"
    if n <= 24:   return "21-24"
    if n <= 32:   return "25-32"
    if n <= 48:   return "33-48"
    if n <= 64:   return "49-64"
    if n <= 128:  return "65-128"
    if n <= 256:  return "129-256"
    return ">256"


def bucketize_cluster_memory_gb(gb):
    # Doubling scale matching how RAM physically ships; floor at "<4".
    if gb < 4:     return "<4"
    if gb < 8:     return "4-8"
    if gb < 16:    return "8-16"
    if gb < 32:    return "16-32"
    if gb < 64:    return "32-64"
    if gb < 128:   return "64-128"
    if gb < 256:   return "128-256"
    if gb < 512:   return "256-512"
    if gb < 1024:  return "512-1024"
    if gb < 2048:  return "1024-2048"
    if gb < 4096:  return "2048-4096"
    return ">4096"


# `frame_memory_gb_bucket` shares boundaries with cluster_memory_gb_bucket.
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


# sort_metric must be one of this fixed lowercase set, and is required. H2O
# AutoML's own metric names map onto it case-insensitively, so we lowercase;
# anything unrecognized falls back to "auto" (AutoML's default) so an odd
# metric can never drop the event.
_ALLOWED_SORT_METRICS = frozenset((
    "auto", "deviance", "logloss", "rmse", "mse", "mae",
    "rmsle", "auc", "aucpr", "mean_per_class_error",
))


def _normalize_sort_metric(metric):
    if metric is not None:
        s = str(metric).strip().lower()
        if s in _ALLOWED_SORT_METRICS:
            return s
    return "auto"


# -- Runtime version detection --

_JAVA_VERSION_RE = re.compile(r'version\s+"([^"]+)"')
_JAVA_PROP_RE   = re.compile(r'^\s*(java\.version|java\.vendor)\s*=\s*(.+?)\s*$', re.MULTILINE)


def _resolve_java_bin():
    """Resolve the java binary, honoring JAVA_HOME before PATH.

    Returns None rather than the bare macOS ``/usr/bin/java`` stub, which pops
    an "install a JDK" dialog when no JDK is present — telemetry must never
    produce user-visible side effects.
    """
    jh = os.environ.get("JAVA_HOME")
    if jh:
        cand = os.path.join(jh, "bin", "java.exe" if os.name == "nt" else "java")
        return cand if os.path.isfile(cand) else None
    java = shutil.which("java")
    if not java:
        return None
    if platform.system() == "Darwin" and os.path.realpath(java) == "/usr/bin/java":
        try:
            proc = subprocess.run(["/usr/libexec/java_home"],
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=2.0)
            home = (proc.stdout or b"").decode("utf-8", "replace").strip()
            if proc.returncode != 0 or not home:
                return None
            cand = os.path.join(home, "bin", "java")
            return cand if os.path.isfile(cand) else None
        except Exception:
            return None
    return java


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
        java_bin = _resolve_java_bin()
        if java_bin is None:
            _java_info_cache = info
            return info
        try:
            proc = subprocess.run(
                [java_bin, "-XshowSettings:properties", "-version"],
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


# -- Host fingerprint — cpu_arch + python_distribution --
# Both are caller-side context carried only on `init` / `cluster_connect`.

def _detect_cpu_arch():
    """Map ``platform.machine()`` to the closed cpu_arch Literal. Never raises.

    ``arm64`` (macOS / Apple Silicon) and ``aarch64`` (Linux ARM64) are kept
    deliberately distinct even though they are the same ISA — the OS-native
    string lets analytics separate Apple-Silicon-Mac from Graviton-Linux
    without joining on the `os` field.
    """
    try:
        machine = platform.machine().lower()
    except Exception:
        return "other"
    if machine in ("x86_64", "amd64"):
        return "x86_64"
    if machine == "arm64":
        return "arm64"
    if machine == "aarch64":
        return "aarch64"
    if machine in ("ppc64le", "ppc64"):
        return "ppc64le"
    if machine == "s390x":
        return "s390x"
    return "other"


def _detect_python_distribution():
    """Read the INSTALLER file the install tool itself wrote.

    Not a CONDA_PREFIX heuristic — pip-into-a-conda-env is real and the
    heuristic would mislabel it. Returns None when the `h2o` distribution
    can't be located (e.g. running from a source checkout). Never raises.
    """
    try:
        import importlib.metadata as _im
    except Exception:
        return None
    try:
        dist = _im.distribution("h2o")
    except Exception:
        return None  # PackageNotFoundError (source checkout) or anything else
    try:
        installer_raw = dist.read_text("INSTALLER")
    except Exception:
        installer_raw = None
    if installer_raw is None:
        return "system"  # no INSTALLER file → installed by a system package manager
    installer = installer_raw.strip().lower()
    if installer == "pip":
        return "pip"
    if installer == "conda":
        return "conda"
    return "other"


# Computed once at import — no per-emit overhead.
_CPU_ARCH = _detect_cpu_arch()
_PYTHON_DIST = _detect_python_distribution()


# -- Cluster-shape detection --

_KUBERNETES_ENV_HINTS = ("KUBERNETES_SERVICE_HOST",)
_HADOOP_ENV_HINTS = ("HADOOP_HOME", "HADOOP_CONF_DIR", "HADOOP_PREFIX")


def _derive_cluster_topology(cloud_size, hadoop_version=None):
    """Derive the cluster_topology enum.

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

    The ``hadoop_version`` field on CloudV3 (populated by h2odriver via the
    ``-ga_hadoop_ver`` flag) is the authoritative signal for the
    ``multi_node_hadoop`` topology, regardless of whether the client itself
    runs on a Hadoop edge node.
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
        # Server-side Hadoop signal trumps client-side env-var heuristics —
        # a workstation client connecting to a Hadoop-launched cluster has no
        # HADOOP_HOME locally, but the cluster knows its own provenance.
        hv = None
        try:
            hv = getattr(cluster, "hadoop_version", None) or None
        except Exception:
            pass
        out["cluster_topology"] = _derive_cluster_topology(cloud_size, hadoop_version=hv)
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
        "session_id": _current_session_id(),
        "ts": int(time.time()),
        # build-flavor attribution (OSS vs Enterprise), on every event.
        "product": _PRODUCT,
    }


def _attach_extras(payload, attributes=None):
    """Add the nullable `attributes` field if any keys were supplied."""
    if attributes:
        # Attribute values must be strings.
        payload["attributes"] = {str(k): str(v) for k, v in attributes.items() if v is not None}
    return payload


def _attributes_with_distribution(attributes):
    """Merge ``attributes.distribution`` (h2o vs h2o_client) for the session-start
    events. A caller-supplied ``distribution`` wins. Returns a new dict (never
    mutates the caller's), so it is safe to call with ``attributes=None``.
    """
    merged = dict(attributes) if attributes else {}
    merged.setdefault("distribution", _DISTRIBUTION)
    return merged


# Single background worker drains a bounded queue, so emitting many events
# (e.g. a tight import/parse loop) never spawns a thread per event.
_QUEUE_MAXSIZE = 64
_telemetry_queue = None
_telemetry_worker = None
_worker_lock = threading.Lock()


def _telemetry_worker_loop():
    while True:
        url, payload, enrich = _telemetry_queue.get()
        try:
            # Any expensive field-gathering (e.g. the `java -version` subprocess)
            # is deferred to here so it runs off the caller's thread.
            if enrich is not None:
                try:
                    enrich(payload)
                except Exception:
                    pass
            req = urllib.request.Request(
                url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            urllib.request.urlopen(req, timeout=_TIMEOUT_SECONDS).read()
        except Exception:
            pass
        finally:
            _telemetry_queue.task_done()


def _ensure_worker():
    global _telemetry_queue, _telemetry_worker
    if _telemetry_worker is not None and _telemetry_worker.is_alive():
        return
    with _worker_lock:
        if _telemetry_queue is None:
            _telemetry_queue = queue.Queue(maxsize=_QUEUE_MAXSIZE)
        if _telemetry_worker is None or not _telemetry_worker.is_alive():
            _telemetry_worker = threading.Thread(
                target=_telemetry_worker_loop, name="h2o-telemetry", daemon=True)
            _telemetry_worker.start()


def _post_async(payload, enrich=None):
    """Enqueue a payload for the background worker. Returns immediately.

    ``enrich`` is an optional callable run on the worker thread to add fields
    that are too expensive to compute on the caller's thread (e.g. probing the
    Java runtime). It must never raise into the worker (it is wrapped there too).
    """
    if _telemetry_disabled():
        return
    url = _resolve_url()
    _ensure_worker()
    try:
        # Never block the caller; drop the event if the backlog is saturated.
        _telemetry_queue.put_nowait((url, payload, enrich))
    except queue.Full:
        pass


def _strip_none(d):
    """Drop None values from a dict (omitted keys are treated as null)."""
    return {k: v for k, v in d.items() if v is not None}


def _never_raises(fn):
    """Wrap a public emitter so a telemetry bug can never surface in a user call.

    Belt-and-suspenders alongside the try/except at every call site: telemetry
    must never break, slow, or fail the h2o-py call it is attached to.
    """
    @functools.wraps(fn)
    def _wrapped(*args, **kwargs):
        try:
            return fn(*args, **kwargs)
        except Exception:
            return None
    return _wrapped


def _enrich_with_java(payload):
    """Add ``java_version`` / ``java_vendor`` to a session-start payload.

    Runs on the telemetry worker thread (via the ``enrich`` hook) so the
    ``java -version`` subprocess never blocks ``h2o.init()`` / ``h2o.connect()``.
    Best-effort and cached after the first probe.
    """
    jv = _java_version_safe()
    if jv is not None:
        payload["java_version"] = jv
    jd = _java_vendor_safe()
    if jd is not None:
        payload["java_vendor"] = jd


# -- First-run disclosure notice ----------------------------------------------

_NOTICE_TEXT = (
    "H2O-3 collects anonymous usage telemetry (H2O version, OS, algorithm names, and\n"
    "coarse usage buckets) to help prioritize features and platforms. It never sends\n"
    "your code, data, file paths, or any identifiers.\n"
    "To opt out: set DO_NOT_TRACK=1, call h2o.set_telemetry(False) (persistent),\n"
    "or pass telemetry=False to h2o.init() / h2o.connect().\n"
    "Docs: https://docs.h2o.ai/h2o/latest-stable/h2o-docs/telemetry.html\n"
    "(This notice is shown only once.)"
)


def _maybe_print_notice():
    """Print the disclosure notice once per environment, then never again.

    Gated on telemetry being enabled (no point notifying an opted-out user) and
    on a per-client marker under ``~/.h2oai`` that records the notice version, so
    the notice reappears only if the disclosure changes (a ``_NOTICE_VERSION``
    bump) — never merely because H2O was upgraded. Entirely best-effort: any
    failure (no home dir, read-only fs) is swallowed and never blocks startup.
    """
    if _telemetry_disabled():
        return
    try:
        marker = os.path.join(_config_dir(), _NOTICE_MARKER_FILE)
        seen = -1
        try:
            with open(marker) as f:
                seen = int(f.read().strip() or "-1")
        except Exception:
            seen = -1
        if seen >= _NOTICE_VERSION:
            return
        sys.stderr.write("\n" + _NOTICE_TEXT + "\n\n")
        sys.stderr.flush()
        os.makedirs(os.path.dirname(marker), exist_ok=True)
        with open(marker, "w") as f:
            f.write(str(_NOTICE_VERSION) + "\n")
    except Exception:
        pass


# -- Public emitters ----------------------------------------------------------

@_never_raises
def send_init_telemetry(h2o_version, *, cluster_shape=None, attributes=None):
    """Fire one `event=init` POST (local-server-spawn branch).

    Mints a fresh session_id for this process. ``cluster_shape`` is the
    dict returned by :func:`derive_cluster_shape` — pass it in when known.
    """
    if _telemetry_disabled():
        return
    _maybe_print_notice()
    _new_session_id()
    payload = {**_envelope(h2o_version), "event": "init"}
    payload.update(_strip_none({
        "python_version": _python_version_safe(),
        "cpu_arch":            _CPU_ARCH,
        "python_distribution": _PYTHON_DIST,
    }))
    if cluster_shape:
        payload.update(_strip_none(cluster_shape))
    _attach_extras(payload, _attributes_with_distribution(attributes))
    # java_version / java_vendor require a subprocess — gathered on the worker.
    _post_async(payload, enrich=_enrich_with_java)


@_never_raises
def send_cluster_connect_telemetry(h2o_version, *, cluster_shape=None, attributes=None):
    """Fire one `event=cluster_connect` POST (connect-only branch — no local server spawned).

    Same envelope and runtime/cluster-shape fields as ``init``; mints a fresh
    session_id (a connect *is* a new session, just one that didn't start the JVM).
    """
    if _telemetry_disabled():
        return
    _maybe_print_notice()
    _new_session_id()
    payload = {**_envelope(h2o_version), "event": "cluster_connect"}
    payload.update(_strip_none({
        "python_version": _python_version_safe(),
        "cpu_arch":            _CPU_ARCH,
        "python_distribution": _PYTHON_DIST,
    }))
    if cluster_shape:
        payload.update(_strip_none(cluster_shape))
    _attach_extras(payload, _attributes_with_distribution(attributes))
    # java_version / java_vendor require a subprocess — gathered on the worker.
    _post_async(payload, enrich=_enrich_with_java)


@_never_raises
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


@_never_raises
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


@_never_raises
def send_mojo_download(h2o_version, algo, family, outcome, compressed_size_bytes, attributes=None):
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "mojo_download",
        "algo": algo,
        "family": family,
        "outcome": outcome,
        "mojo_size_bucket": bucketize_mojo_size(compressed_size_bytes),
    }
    _attach_extras(payload, attributes)
    _post_async(payload)


@_never_raises
def send_upload(h2o_version, file_format, compressed_size_bytes, outcome,
                *, frame_shape=None, attributes=None):
    """Fire one `event=upload` POST.

    ``frame_shape`` is an optional dict with keys ``rows_bucket``, ``cols_bucket``,
    ``frame_memory_gb_bucket``. Pass ``None`` (or omit) when the parse failed —
    sending bucket values for an error path is misleading.

    ``data_size_bucket`` is required on upload (the client always knows the
    on-disk size of the bytes it is about to push).
    """
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "upload",
        "file_format": file_format,
        "data_size_bucket": bucketize_data_size(compressed_size_bytes),
        "outcome": outcome,
    }
    if frame_shape:
        payload.update(_strip_none(frame_shape))
    _attach_extras(payload, attributes)
    _post_async(payload)


@_never_raises
def send_import(h2o_version, source_scheme, file_format, outcome,
                *, compressed_size_bytes=None, frame_shape=None, attributes=None):
    """Fire one `event=import` POST.

    Optional ``compressed_size_bytes`` (the size of the remote payload) and
    ``frame_shape`` (post-parse) are both omitted on error paths or when the
    size isn't cheap to derive.

    ``data_size_bucket`` is nullable on import (the source is often
    HDFS/S3/GCS where size is metadata the cluster must round-trip to
    discover) — send None / omit when it isn't cheaply known.
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
        payload["data_size_bucket"] = bucketize_data_size(compressed_size_bytes)
    if frame_shape:
        payload.update(_strip_none(frame_shape))
    _attach_extras(payload, attributes)
    _post_async(payload)


@_never_raises
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
        "sort_metric":            _normalize_sort_metric(sort_metric),
        "leaderboard_size_bucket": bucketize_leaderboard_size(int(leaderboard_size)) if leaderboard_size is not None else None,
    }
    payload = _strip_none(payload)
    _attach_extras(payload, attributes)
    _post_async(payload)


@_never_raises
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
        "artifact_size_bucket": bucketize_artifact_size(compressed_size_bytes) if compressed_size_bytes is not None else None,
    }
    payload = _strip_none(payload)
    _attach_extras(payload, attributes)
    _post_async(payload)


@_never_raises
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
        "artifact_size_bucket": bucketize_artifact_size(compressed_size_bytes) if compressed_size_bytes is not None else None,
    }
    payload = _strip_none(payload)
    _attach_extras(payload, attributes)
    _post_async(payload)


@_never_raises
def send_model_download(h2o_version, algo, family, outcome, fmt, compressed_size_bytes, attributes=None):
    """Fire one `event=model_download` POST (non-MOJO downloads).

    ``fmt`` is ``"binary"`` or ``"pojo"`` — never ``"mojo"`` (MOJOs go through
    :func:`send_mojo_download`).
    """
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "model_download",
        "algo": algo,
        "family": family,
        "outcome": outcome,
        "format": fmt,
        "artifact_size_bucket": bucketize_artifact_size(compressed_size_bytes) if compressed_size_bytes is not None else None,
    }
    payload = _strip_none(payload)
    _attach_extras(payload, attributes)
    _post_async(payload)


@_never_raises
def send_frame_parsed(h2o_version, file_format, outcome, duration_ms, n_rows, n_cols,
                      frame_memory_gb=None, attributes=None):
    """Fire one `event=frame_parsed` POST (fires alongside upload / import).

    Captures the parse operation itself. ``rows_bucket`` / ``cols_bucket`` /
    ``duration_ms_bucket`` are required — omitting any of them drops the event.
    Only ``frame_memory_gb_bucket`` is nullable. Callers must only fire this on
    a completed parse (``outcome="ok"`` with a real frame in hand).
    """
    if _telemetry_disabled():
        return
    payload = {
        **_envelope(h2o_version),
        "event": "frame_parsed",
        "file_format": file_format,
        "outcome": outcome,
        "rows_bucket": bucketize_rows(n_rows),
        "cols_bucket": bucketize_cols(n_cols),
        "duration_ms_bucket": bucketize_duration_ms(duration_ms),
    }
    if frame_memory_gb is not None:
        payload["frame_memory_gb_bucket"] = bucketize_frame_memory_gb(frame_memory_gb)
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
    """Return bucket fields for a parsed H2OFrame, or empty dict on failure."""
    if frame is None:
        return {}
    out = {}
    try:
        out["rows_bucket"] = bucketize_rows(int(frame.nrow or 0))
        out["cols_bucket"] = bucketize_cols(int(frame.ncol or 0))
    except Exception:
        pass
    # frame_memory_gb_bucket is intentionally omitted: the H2OFrame client object
    # does not expose an in-memory byte size, and this field is nullable on the wire.
    return out


def derive_frame_dims(frame):
    """Return ``(n_rows, n_cols, frame_memory_gb)`` for a parsed H2OFrame.

    Used by the ``frame_parsed`` call sites, which need raw counts (not
    buckets) and REQUIRE rows + cols. Returns ``None`` when rows/cols can't
    be determined so the caller skips the event. ``frame_memory_gb`` is always
    None: the client object does not expose a byte size (nullable on the wire).
    """
    if frame is None:
        return None
    try:
        n_rows = int(frame.nrow or 0)
        n_cols = int(frame.ncol or 0)
    except Exception:
        return None
    return (n_rows, n_cols, None)
