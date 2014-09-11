setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.hex_1779_cbind <- function(H2Oserver) {

prostatePath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(H2Oserver, path = prostatePath, key = "prostate.hex")

new_col = vector(mode="numeric", dim(prostate.hex)[1])

expect_error(cbind(prostate.hex, new_col))

testEnd()

}

doTest("HEX-1779 cbind of h2o frame to R vector should give error", test.hex_1779_cbind)

