setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

# The H2O-3 Enterprise paywall (marketing demo) blocks getting a model *out* of
# OSS: MOJO download/export (h2o.download_mojo / h2o.save_mojo) and POJO download
# (h2o.download_pojo). MOJO import/upload are intentionally left unblocked. No
# cluster needed: the block is the first statement of the blocked functions, so
# it raises before touching a model or the backend. (Real enforcement is
# server-side; this covers the client-facing message.)

test.enterprise_mojo_block <- function() {

  expect_blocked <- function(expr, op_text) {
    msg <- tryCatch({ force(expr); NULL }, error = function(err) conditionMessage(err))
    expect_false(is.null(msg))                              # it must raise
    expect_true(grepl(op_text, msg, fixed = TRUE))          # names the operation
    expect_true(grepl("requires H2O-3 Enterprise", msg, fixed = TRUE))
    expect_true(grepl("enterprise@h2o.ai", msg, fixed = TRUE))
  }

  expect_blocked(h2o.download_mojo("dummy_model"), "MOJO export")
  expect_blocked(h2o.save_mojo("dummy_model"),     "MOJO export")
  expect_blocked(h2o.download_pojo("dummy_model"), "POJO download")
}

doTest("Enterprise: MOJO export/import/upload blocked in the OSS R client",
       test.enterprise_mojo_block)
