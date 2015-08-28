setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pub_697_exec_bad_key_name <- function(H2Oserver) {

prostatePath = locate("smalldata/prostate/prostate.csv")
prostate.hex = h2o.importFile(path = prostatePath, destination_frame = "prostate.hex")

prostate.local = as.data.frame(prostate.hex)

# Are we in the right universe?
expect_equal(380, dim(prostate.local)[1])
expect_equal(9, dim(prostate.local)[2])

remote = t(prostate.hex$AGE) %*% prostate.hex$CAPSULE
expect_equal(1, dim(remote)[1])
expect_equal(1, dim(remote)[2])

expect_error(t(pub697$AGE) %*% prostate.hex$CAPSULE)

testEnd()

}

doTest("PUB-697 bad key should not cause crash", test.pub_697_exec_bad_key_name)

