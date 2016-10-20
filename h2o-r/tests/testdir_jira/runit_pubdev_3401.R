setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_3401 <- function() {
    files <- c(locate("smalldata/jira/adult.gz"), locate("smalldata/jira/pubdev_3401-adult-1000.gz"))

    data.raw <- h2o.importFile(files, destination_frame = "pubdev3401.raw", parse = FALSE)
    data.parsed <- h2o.parseRaw(data.raw, destination_frame = "pubdev3401.parsed",
                                parse_type = "CSV", chunk_size = 105000, header = FALSE)

    nr <- nrow(data.parsed)
    expect_equal(nr, 48842 + 1000)
}

doTest("PUBDEV-3401: NPE produced in multi-file parse", test.pubdev_3401)
