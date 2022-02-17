setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../../h2o-r/scripts/h2o-r-test-setup.R")

test.import.hive <- function() {

    test_table_normal <- h2o.import_hive_table("default", "test_table_normal")
    expect_equal(nrow(test_table_normal),3)
    expect_equal(ncol(test_table_normal),5)
    
    test_table_multi_key <- h2o.import_hive_table("default", "test_table_multi_key", list(c("2017", "1")))
    expect_equal(nrow(test_table_multi_key),2)
    expect_equal(ncol(test_table_multi_key),5)

}

doTest("Import Hive Data", test.import.hive)
