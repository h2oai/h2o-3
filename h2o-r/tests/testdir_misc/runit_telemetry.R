setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

# R telemetry is limited to the two session-start events (init / cluster_connect),
# delivered by one synchronous, bounded curl POST. No cluster is needed: we test
# the payload-builder helpers the emitters assemble (envelope, runtime fields,
# distribution attribute), the shared cluster-shape bucket boundaries, the
# opt-out / persistence logic, the one-time notice, and that the emitters are
# exception-safe (never raise). Internal functions are exposed unqualified via
# `additional_imports` in h2o-r-test-setup.R (never via the ::: operator, which
# is unavailable when the package source is sourced rather than installed).

test.telemetry <- function() {
  ver <- "3.46.0.12"

  # --- Envelope: the fields every event carries. ---
  env <- .h2o.telemetry.envelope(ver)
  for (k in c("payload_version", "client", "h2o_version", "os", "session_id", "ts", "product"))
    expect_true(!is.null(env[[k]]))
  expect_equal(env$client, "r")
  expect_equal(env$h2o_version, ver)
  expect_equal(env$payload_version, 1L)
  expect_equal(env$product, "h2o-3-oss")
  expect_true(env$os %in% c("macos", "windows", "linux") || nzchar(env$os))

  # --- Runtime fields on init / cluster_connect: r_version + cpu_arch always
  #     present; python_* never present on the R client. ---
  rf <- .h2o.telemetry.runtime_fields()
  expect_true("r_version" %in% names(rf))
  expect_true("cpu_arch" %in% names(rf))
  expect_false("python_version" %in% names(rf))
  expect_false("python_distribution" %in% names(rf))

  # --- Build-flavor distribution attribute (h2o vs h2o_client; falls back to
  #     "h2o" for a source/dev load with no baked marker). ---
  attrs <- .h2o.telemetry.attributes_with_distribution(NULL)
  expect_true(attrs$distribution %in% c("h2o", "h2o_client"))

  # --- Cluster-shape bucket boundaries (shared wire contract across clients). ---
  expect_equal(bucketize_cluster_nodes(1),   "1")     # exact count for n <= 16
  expect_equal(bucketize_cluster_nodes(16),  "16")
  expect_equal(bucketize_cluster_nodes(17),  "17-20")
  expect_equal(bucketize_cluster_nodes(256), "129-256")
  expect_equal(bucketize_cluster_nodes(257), ">256")
  expect_equal(bucketize_cluster_memory_gb(3),    "<4")
  expect_equal(bucketize_cluster_memory_gb(4),    "4-8")
  expect_equal(bucketize_cluster_memory_gb(16),   "16-32")
  expect_equal(bucketize_cluster_memory_gb(5000), ">4096")  # large value does not overflow

  # Use a throwaway HOME so persistence / notice markers never touch the real
  # one -- and so the test does not depend on a real (or writable) home existing
  # on CI (tempdir() is always present). DO_NOT_TRACK is unset for the duration
  # (some CI set it) and restored on exit.
  old_home <- Sys.getenv("HOME")
  old_url <- Sys.getenv("H2O_TELEMETRY_URL"); old_dnt <- Sys.getenv("DO_NOT_TRACK")
  tmp_home <- tempfile("h2o_home"); dir.create(tmp_home)
  Sys.setenv(HOME = tmp_home)
  on.exit({
    Sys.setenv(HOME = old_home)
    if (nzchar(old_url)) Sys.setenv(H2O_TELEMETRY_URL = old_url) else Sys.unsetenv("H2O_TELEMETRY_URL")
    if (nzchar(old_dnt)) Sys.setenv(DO_NOT_TRACK = old_dnt) else Sys.unsetenv("DO_NOT_TRACK")
  }, add = TRUE)
  Sys.unsetenv("DO_NOT_TRACK")

  # --- First-run disclosure notice: correct text, shown once per environment.
  #     Asserted via the version-stamped marker (the actual once-only mechanism)
  #     rather than by capturing message() output -- the runit harness already
  #     holds a sink on the message stream, so capture.output(type="message")
  #     would grab nothing. ---
  expect_true(any(grepl("anonymous usage telemetry", .h2o.telemetry.notice_text())))
  marker <- file.path(tmp_home, ".h2oai", ".telemetry_notice_r")
  if (file.exists(marker)) file.remove(marker)
  h2o.set_telemetry(TRUE)               # enabled, so the notice is eligible
  .h2o.telemetry.maybe_print_notice()   # first run: prints + records the marker
  expect_true(file.exists(marker))
  writeLines("999", marker)             # sentinel above the notice version
  .h2o.telemetry.maybe_print_notice()   # second run: marker >= version -> no-op
  expect_equal(readLines(marker, warn = FALSE), "999")

  # --- Persistent opt-out: h2o.set_telemetry writes ~/.h2oai/telemetry and the
  #     getter reflects it (kept in sync with the Python client). ---
  pref <- file.path(tmp_home, ".h2oai", "telemetry")
  expect_true(h2o.set_telemetry(FALSE))
  expect_equal(readLines(pref, warn = FALSE), "0")
  expect_false(h2o.telemetry_enabled())
  expect_true(h2o.set_telemetry(TRUE))
  expect_equal(readLines(pref, warn = FALSE), "1")
  expect_true(h2o.telemetry_enabled())

  # --- ~/.h2oconfig (home) opt-out; any opt-out wins (union). ---
  file.remove(pref)
  cfg <- file.path(tmp_home, ".h2oconfig")
  disabled_after <- function() { .h2o.telemetry.load_persisted_pref(); !h2o.telemetry_enabled() }

  writeLines(c("[general]", "telemetry = false"), cfg)
  expect_true(disabled_after())                 # config opt-out disables

  writeLines("general.telemetry = true", cfg)
  expect_false(disabled_after())                # config opt-in enables

  writeLines("1", pref); writeLines(c("[general]", "telemetry = off"), cfg)
  expect_true(disabled_after())                 # config off wins over ~/.h2oai file on (union off)
  file.remove(pref); file.remove(cfg)

  # --- DO_NOT_TRACK always wins. ---
  h2o.set_telemetry(TRUE)
  Sys.setenv(DO_NOT_TRACK = "1")
  expect_true(.h2o.telemetry.disabled())
  Sys.unsetenv("DO_NOT_TRACK")
  expect_false(.h2o.telemetry.disabled())

  # --- Emitters are exception-safe: bounded synchronous POST to an unreachable
  #     endpoint never raises, whether telemetry is on or off. ---
  Sys.setenv(H2O_TELEMETRY_URL = "http://127.0.0.1:1/v1/event")  # refused fast; bounded by connecttimeout
  h2o.set_telemetry(TRUE)
  expect_error(.h2o.send_init_telemetry(ver), NA)
  expect_error(.h2o.send_cluster_connect_telemetry(ver), NA)
  h2o.set_telemetry(FALSE)  # disabled -> no-op, no network
  expect_error(.h2o.send_init_telemetry(ver), NA)
  h2o.set_telemetry(TRUE)   # restore default-on for the session
}

doTest("Telemetry: session-start payload, buckets, opt-out (no cluster needed)",
       test.telemetry)
