setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.import.sql <- function() {
  f = h2o.import_sql_table("jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false", "citibike20k", "root", "0xdata")
  expect_equal(nrow(f),2e4)
  expect_equal(ncol(f),15)

  f = h2o.import_sql_table("jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false", "citibike20k", "root", "0xdata", c("bikeid", "starttime"))
  expect_equal(ncol(f), 2)
}

doTest("Test import sql table", test.import.sql)
