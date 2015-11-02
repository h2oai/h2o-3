


test.separator <- function() {
    path <- locate("smalldata/jira/hexdev_29.csv")

    fhex = h2o.importFile(path, sep=",")
    expect_equal(sum(sapply(1:ncol(fhex), function (c) sum(is.na(fhex[,c])))), 0)

    fhex_wrong_separator = h2o.importFile(path, sep=";")
    expect_equal(nrow(fhex_wrong_separator), 6)
    expect_equal(ncol(fhex_wrong_separator), 1)

    expect_error(h2o.importFile(path, sep="--"))

    
}

doTest("Separator", test.separator)
