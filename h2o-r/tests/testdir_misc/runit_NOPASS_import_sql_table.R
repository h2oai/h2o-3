setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.import.sql <- function() {
  mySqlConnUrl <- "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
  username <- "root"
  password <- "0xdata"
  table <- "citibike20k"
  if (Sys.getenv('SQLCONNURL') == mySqlConnUrl) {
    f = h2o.import_sql_table(mySqlConnUrl, table, username, password)
    expect_equal(nrow(f),2e4)
    expect_equal(ncol(f),15)

    f = h2o.import_sql_table(mySqlConnUrl, table, username, password, c("bikeid", "starttime"))
    expect_equal(nrow(f),2e4)
    expect_equal(ncol(f), 2)

    f = h2o.import_sql_select(mySqlConnUrl, "SELECT bikeid from citibike20k", username, password)
    expect_equal(nrow(f),2e4)
    expect_equal(ncol(f), 1)
  }
}

doTest("Test import sql table", test.import.sql)
