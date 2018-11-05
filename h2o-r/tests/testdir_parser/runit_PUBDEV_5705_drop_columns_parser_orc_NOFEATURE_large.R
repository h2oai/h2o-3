setwd(normalizePath(dirname(
R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing with skipped columns
test.parseSkippedColumnsOrc<- function() {
    f1 <-
    h2o.importFile(locate("smalldata/parser/csv2orc/prostate_NA.csv"))
    fileName <- locate("smalldata/parser/orc/prostate_NA.orc")

    orcFile <- h2o.importFile(fileName)
    compareFrames(as.data.frame(f1), as.data.frame(orcFile))

    fullFrameR <- as.data.frame(f1)
    skip_front <- c(1)
    skip_end <- c(h2o.ncol(f1))
    set.seed <- 12345
    onePermute <- sample(h2o.ncol(f1))
    skipall <- onePermute
    skip90Per <- onePermute[1:floor(h2o.ncol(f1) * 0.9)]

    # test skipall for h2o.importFile
    e <-
    tryCatch(
    assertCorrectSkipColumns(fileName, fullFrameR, skipall, TRUE, h2o.getTypes(f1)),
    error = function(x)
    x
    )
    print(e)
    # test skipall for h2o.uploadFile
    e2 <-
    tryCatch(
    assertCorrectSkipColumns(fileName, fullFrameR, skipall, FALSE, h2o.getTypes(f1)),
    error = function(x)
    x
    )
    print(e2)

    # skip 90% of the columns randomly
    print("Testing skipping 99% of columns")
    assertCorrectSkipColumns(fileName, fullFrameR, skip90Per, TRUE, h2o.getTypes(f1)) # test importFile
    assertCorrectSkipColumns(fileName, fullFrameR, skip90Per, FALSE, h2o.getTypes(f1)) # test uploadFile
}

doTest("Test Orc Parse with skipped columns", test.parseSkippedColumnsOrc)
