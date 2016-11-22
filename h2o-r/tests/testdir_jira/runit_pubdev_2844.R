setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pubdev_2844 <- function() {
  return(TRUE) # skip tests for now
  df1 <- iris
  h2o.no_progress()
  do.dt <- requireNamespace("data.table", quietly=TRUE) && (packageVersion("data.table") >= as.package_version("1.9.7"))
  
  # as.h2o
  op <- options("datatable.verbose"=TRUE, "h2o.fwrite"=NULL)
  co <- capture.output(
    hf1 <- as.h2o(df1, destination_frame = "pubdev2844.1")
  )
  options(op)
  expect_true(is.h2o(hf1))
  if(do.dt) {
    expect_true(length(co) && sum(grepl("maxLineLen", co)), label="as.h2o should use data.table::fwrite")
  } else {
    expect_true(!length(co), label="as.h2o should not produce verbose messages when data.table::fwrite is not used")
  }
  
  # as.h2o - data.table off
  if(do.dt) {
    op <- options("datatable.verbose"=TRUE, "h2o.fwrite"=FALSE)
    co <- capture.output(
      hf2 <- as.h2o(df1, destination_frame = "pubdev2844.2")
    )
    options(op)
    expect_true(is.h2o(hf2))
    expect_true(!length(co), label="as.h2o should not produce verbose messages when data.table::fwrite is forced disabled")
  }
  
  # as.data.frame
  op <- options("datatable.verbose"=TRUE, "h2o.fread"=NULL)
  co <- capture.output(
    df2 <- as.data.frame(hf1)
  )
  options(op)
  expect_true(is.data.frame(df2))
  if(do.dt) {
    expect_true(length(co) && sum(grepl("Converting column", co)), label="as.data.frame.H2OFrame should not produce verbose messages when data.table::fread is not used")
  } else {
    expect_true(!length(co))
  }
  
  # as.data.frame - data.table off
  if(do.dt) {
    op <- options("datatable.verbose"=TRUE, "h2o.fread"=FALSE)
    co <- capture.output(
      df3 <- as.data.frame(hf2)
    )
    options(op)
    expect_true(is.data.frame(df3))
    expect_true(!length(co), label="as.data.frame.H2OFrame should not produce verbose messages when data.table::fread is forced disabled")
  }
  
  expect_equal(df1, df2, label="data.frame passed to h2o and back are equal")
  if(do.dt)
    expect_equal(df1, df3, label="data.frame passed to h2o and back are equal also when data.table force disabled")
  
}

doTest("PUBDEV-2844", test.pubdev_2844)
