setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Telemetry needs no cluster: we stub the transport and assert the payloads the
# public emitters build (envelope + bucket label strings). The real non-blocking
# transport (a libcurl async multi pool) is covered by the Python/Java HTTP smoke
# tests; R has no lightweight in-process HTTP server, so we don't duplicate a
# delivery test here.

test.telemetry <- function() {
  check <- function(cond, msg) if (!isTRUE(cond)) stop("FAIL: ", msg)

  # Make sure telemetry is enabled for the duration of the test.
  Sys.unsetenv("DO_NOT_TRACK")
  h2o:::.h2o.telemetry.state$disabled_by_kwarg <- FALSE

  # Intercept the transport: capture each payload instead of POSTing it.
  captured <- new.env()
  captured$events <- list()
  orig_post <- h2o:::.h2o.telemetry.post
  assignInNamespace(".h2o.telemetry.post",
                    function(payload) {
                      captured$events[[length(captured$events) + 1L]] <- payload
                      invisible(NULL)
                    },
                    ns = "h2o")
  on.exit(assignInNamespace(".h2o.telemetry.post", orig_post, ns = "h2o"), add = TRUE)

  ver <- "3.46.0.12"
  h2o:::.h2o.send_import(ver, "hdfs", "csv", "ok",
                         compressed_size_bytes = 50 * 1024 * 1024,
                         frame_shape = list(rows_bucket = "1K-10K", cols_bucket = "1-10"))
  h2o:::.h2o.send_init_telemetry(ver)

  by_event <- list()
  for (e in captured$events) by_event[[e$event]] <- e

  check(!is.null(by_event[["import"]]), "import event emitted")
  check(!is.null(by_event[["init"]]),   "init event emitted")

  imp <- by_event[["import"]]
  for (k in c("payload_version", "client", "h2o_version", "session_id", "ts", "product"))
    check(!is.null(imp[[k]]), paste("envelope key present:", k))
  check(identical(imp$client, "r"),                "client = r")
  check(identical(imp$source_scheme, "hdfs"),      "source_scheme = hdfs")
  check(identical(imp$file_format, "csv"),         "file_format = csv")
  check(identical(imp$outcome, "ok"),              "outcome = ok")
  check(identical(imp$data_size_bucket, "10MB-100MB"),
        paste("data_size_bucket bucketed, got:", imp$data_size_bucket))
  check(identical(imp$rows_bucket, "1K-10K"),      "rows_bucket carried through")

  # init carries the runtime fields (r_version always) and the build-flavor
  # distribution attribute (h2o vs h2o_client; falls back to "h2o" for a
  # source/dev install with no baked marker).
  check(!is.null(by_event[["init"]]$r_version), "init event carries r_version")
  dist <- by_event[["init"]]$attributes$distribution
  check(!is.null(dist) && dist %in% c("h2o", "h2o_client"),
        paste("init event has attributes.distribution, got:", dist))

  # Bucket boundaries are byte-exact (shared wire contract across clients).
  check(identical(h2o:::bucketize_data_size(9 * 1024 * 1024),  "<10MB"),      "data_size <10MB boundary")
  check(identical(h2o:::bucketize_data_size(10 * 1024 * 1024), "10MB-100MB"), "data_size 10MB boundary")
  check(identical(h2o:::bucketize_data_size(5 * 1024^3),       "5GB-10GB"),   "data_size 5GB boundary")

  # A >2GB file must not overflow to NA (the as.integer bug) — it buckets cleanly.
  check(identical(h2o:::bucketize_data_size(3 * 1024^3), "1GB-5GB"),
        "files larger than 2GB bucket correctly")

  # First-run disclosure notice: shown once per environment, then suppressed.
  # Use a throwaway HOME so the marker doesn't touch the real one.
  old_home <- Sys.getenv("HOME")
  tmp_home <- tempfile("h2o_home"); dir.create(tmp_home)
  Sys.setenv(HOME = tmp_home)
  on.exit(Sys.setenv(HOME = old_home), add = TRUE)
  h2o:::.h2o.telemetry.state$disabled_by_kwarg <- FALSE
  first  <- capture.output(h2o:::.h2o.telemetry.maybe_print_notice(), type = "message")
  second <- capture.output(h2o:::.h2o.telemetry.maybe_print_notice(), type = "message")
  check(any(grepl("anonymous usage telemetry", first)), "notice printed on first run")
  check(length(second) == 0L, "notice not repeated on second run (marker honored)")

  # Persistent opt-out: h2o.set_telemetry writes ~/.h2oai/telemetry (kept in sync with Python).
  pref <- file.path(tmp_home, ".h2oai", "telemetry")
  check(isTRUE(h2o::h2o.set_telemetry(FALSE)), "set_telemetry(FALSE) persisted to disk")
  check(identical(readLines(pref, warn = FALSE), "0"), "pref file written as 0")
  check(identical(h2o::h2o.telemetry_enabled(), FALSE), "telemetry_enabled() FALSE after opt-out")
  check(isTRUE(h2o::h2o.set_telemetry(TRUE)), "set_telemetry(TRUE) persisted to disk")
  check(isTRUE(h2o::h2o.telemetry_enabled()), "telemetry_enabled() TRUE after opt-in")

  # ~/.h2oconfig (home) opt-out; any opt-out wins (union). Tested in isolation.
  file.remove(pref)
  cfg <- file.path(tmp_home, ".h2oconfig")
  reload <- function() {
    h2o:::.h2o.telemetry.state$disabled_by_kwarg <- FALSE
    h2o:::.h2o.telemetry.load_persisted_pref()
    h2o:::.h2o.telemetry.disabled()
  }
  writeLines(c("[general]", "telemetry = false"), cfg)
  check(isTRUE(reload()), ".h2oconfig telemetry=false opts out")
  writeLines("general.telemetry = true", cfg)
  check(identical(reload(), FALSE), ".h2oconfig general.telemetry=true opts in")
  writeLines("1", pref); writeLines(c("[general]", "telemetry = off"), cfg)
  check(isTRUE(reload()), "config opt-out wins over ~/.h2oai file (union off)")
  file.remove(pref); file.remove(cfg)
  h2o:::.h2o.telemetry.state$disabled_by_kwarg <- FALSE

  # Disabled telemetry emits nothing.
  h2o:::.h2o.telemetry.state$disabled_by_kwarg <- TRUE
  before <- length(captured$events)
  h2o:::.h2o.send_import(ver, "s3", "parquet", "ok")
  check(length(captured$events) == before, "no events emitted while telemetry disabled")
  h2o:::.h2o.telemetry.state$disabled_by_kwarg <- FALSE
}

doTest("Telemetry: wire contract + bucket boundaries (no cluster needed)", test.telemetry)
