setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.pubdev_3590 <- function() {
    data.path <- locate("smalldata/jira/runit_pubdev_3590_unexpected_column.csv")

    expected.warning <- sprintf("ParseError at file %s at line 3 ( destination line 3 ); error = '%s'", data.path,
        "Invalid line, found more columns than expected (found: 4, expected: 2); values = {5.0, e}")

    expect_warning(data <- h2o.importFile(data.path), expected.warning, fixed = TRUE)

    expect_equal(nrow(data), 6)
}

doTest("PUBDEV-3590: Warning is produced if CSV file has unexpected number of columns", test.pubdev_3590)
