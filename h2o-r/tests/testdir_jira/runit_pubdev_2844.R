setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pubdev_2844 <- function() {
  
  df1 <- iris
  h2o.no_progress()
  
  # as.h2o
  op <- options("datatable.verbose"=TRUE, "h2o.use.data.table"=TRUE)
  co <- capture.output(
    hf1 <- as.h2o(df1, destination_frame = "pubdev2844.1")
  )
  expect_true(is.h2o(hf1))
  if (use.package("data.table")) {
    expect_true(length(co) && sum(grepl("maxLineLen", co)), label="as.h2o should produce verbose messages when data.table::fwrite verbose=TRUE is used")
  } else {
    expect_true(!length(co), label="as.h2o should not produce verbose messages when data.table::fwrite is not used")
  }
  options(op)
  
  # as.h2o - data.table off
  op <- options("datatable.verbose"=TRUE, "h2o.use.data.table"=FALSE)
  co <- capture.output(
    hf2 <- as.h2o(df1, destination_frame = "pubdev2844.2")
  )
  options(op)
  expect_true(is.h2o(hf2))
  expect_true(!length(co), label="as.h2o should not produce verbose messages when data.table::fwrite is forced disabled")
  
  # as.data.frame
  op <- options("datatable.verbose"=TRUE, "h2o.use.data.table"=TRUE)
  co <- capture.output(
    df2 <- as.data.frame(hf1)
  )
  expect_true(is.data.frame(df2))
  if (use.package("data.table")) {
    expect_true(as.logical(length(co)), label="as.data.frame.H2OFrame should produce verbose messages when data.table::fread verbose=TRUE is used")
  } else {
    expect_true(!length(co), label="as.data.frame.H2OFrame should not produce verbose messages when data.table::fread is not used")
  }
  options(op)
  
  # as.data.frame - data.table off
  op <- options("datatable.verbose"=TRUE, "h2o.use.data.table"=FALSE)
  co <- capture.output(
    df3 <- as.data.frame(hf2)
  )
  options(op)
  expect_true(is.data.frame(df3))
  expect_true(!length(co), label="as.data.frame.H2OFrame should not produce verbose messages when data.table::fread is forced disabled")
  
  expect_equal(df1, df2, label="data.frame passed to h2o and back are equal")
  expect_equal(df1, df3, label="data.frame passed to h2o and back are equal also when data.table force disabled")
  
  # test "h2o.verbose" option to measure timing and also confirm fwrite/fread
  op <- options(
    "h2o.verbose"=TRUE,
    "h2o.use.data.table"=TRUE,
    "datatable.verbose"=FALSE
  )
  co <- capture.output(
    hf3 <- as.h2o(df1, destination_frame = "pubdev2844.3")
  )
  expect_true(is.h2o(hf3))
  if (use.package("data.table")) {
    expect_true(length(co) && sum(grepl("fwrite", co)), label="as.h2o should produce 'fwrite' in timing message when h2o.verbose=TRUE and data.table used.")
  } else {
    expect_true(length(co) && sum(grepl("write.csv", co)), label="as.h2o should produce 'write.csv' in timing message when h2o.verbose=TRUE and data.table not used.")
  }
  # other way around
  co <- capture.output(
    df4 <- as.data.frame(hf3)
  )
  expect_true(is.data.frame(df4))
  if (use.package("data.table")) {
    expect_true(length(co) && sum(grepl("fread", co)), label="as.data.frame.H2OFrame should produce 'fread' in timing message when h2o.verbose=TRUE and data.table used.")
  } else {
    expect_true(length(co) && sum(grepl("read.csv", co)), label="as.data.frame.H2OFrame should produce 'read.csv' in timing message when h2o.verbose=TRUE and data.table not used.")
  }
  options(op)
}

doTest("PUBDEV-2844", test.pubdev_2844)
