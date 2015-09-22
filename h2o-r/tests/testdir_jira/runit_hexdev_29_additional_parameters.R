setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.additional.parameters <- function() {
    dest_frame <- "dev29hex"
    c.names <- c("a", "b", "c")
    c.types <- c("enum", "enum", "enum")

    fhex <- h2o.importFile(locate("smalldata/jira/hexdev_29.csv"), destination_frame=dest_frame, col.names=c.names,
                           col.types=c.types)

    expect_true(all(colnames(fhex) == c.names))
    expect_true(all(sapply(1:ncol(fhex), function (c) is.factor(fhex[,c]))))

    testEnd()
}

doTest("Additional parameters", test.additional.parameters)