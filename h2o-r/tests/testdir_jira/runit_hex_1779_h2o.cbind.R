setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.hex_1779_h2o.cbind <- function(H2Oserver) {

prostatePath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.importFile(H2Oserver, path = prostatePath, key = "prostate.hex")

new_col <- vector(mode="numeric", dim(prostate.hex)[1])

expect_error(h2o.cbind(prostate.hex, new_col))

testEnd()

}

doTest("HEX-1779 h2o.cbind of h2o frame to R vector should give error", test.hex_1779_h2o.cbind)

