setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../h2o-r/scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  Tests import of 30k small Parquet files (no OOM)
#----------------------------------------------------------------------

.skip.test <- function(dir) {
    files <- .h2o.__remoteSend(.h2o.__IMPORT, path=dir)$files
    return((length(files) == 1) && (endsWith(files, "notest")))
}

check.parquet_huge_import <- function() {
    url <- sprintf("hdfs://%s%s", HADOOP.NAMENODE, "/datasets/parse/parquet/pubdev7293/")
    if (.skip.test(url)) {
        Log.info("Skipping test 'check.parquet_huge_import' - NOTEST file found")
        return()
    }

    imported <- h2o.importFile(url)
    
    expect_equal(nrow(imported), 719993999)
}

doTest("Import of 30k small Parquet files", check.parquet_huge_import)
