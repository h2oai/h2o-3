setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pub_832_cbind <- function(H2Oserver) {

prostatePath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(H2Oserver, path = prostatePath, key = "prostate.hex")

new_col = h2o.runif(prostate.hex, 0, 10)
bound.hex = cbind(prostate.hex, new_col)

expect_that(dim(bound.hex)[1], equals(dim(prostate.hex)[1]))
expect_that(dim(bound.hex)[2], equals(1 + dim(prostate.hex)[2]))

testEnd()
}

doTest("PUB-832 cbind vector group error", test.pub_832_cbind)

