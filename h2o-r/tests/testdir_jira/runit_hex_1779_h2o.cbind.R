


test.hex_1779_h2o.cbind <- function() {

prostatePath <- locate("smalldata/prostate/prostate.csv")
prostate.hex <- h2o.importFile(path = prostatePath, destination_frame = "prostate.hex")

new_col <- vector(mode="numeric", dim(prostate.hex)[1])

expect_error(h2o.cbind(prostate.hex, new_col))



}

doTest("HEX-1779 h2o.cbind of h2o frame to R vector should give error", test.hex_1779_h2o.cbind)

