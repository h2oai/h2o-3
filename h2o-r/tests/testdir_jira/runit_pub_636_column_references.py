setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.pub_636_column_references <- function(H2Oserver) {

prostatePath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(H2Oserver, path = prostatePath, key = "prostate.hex")

prostate.local = as.data.frame(prostate.hex)

# Are we in the right universe?
expect_equal(380, dim(prostate.local)[1])
expect_equal(9, dim(prostate.local)[2])

###########################################################
# test various ways of specifying a column to create or set
###########################################################

# local create of new column
prostate.local$foo = prostate.local$AGE
prostate.local[,"bar"] = prostate.local$AGE
prostate.local["baz"] = prostate.local$AGE

expect_equal(prostate.local$foo[100], prostate.local$AGE[100])
expect_equal(prostate.local$bar[100], prostate.local$AGE[100])
expect_equal(prostate.local$baz[100], prostate.local$AGE[100])


# remote create of new column
prostate.hex$foo = prostate.hex$AGE
prostate.hex[,"bar"] = prostate.hex$AGE
prostate.hex["baz"] = prostate.hex$AGE

expect_equal(prostate.hex$foo[100], prostate.hex$AGE[100])
expect_equal(prostate.hex$bar[100], prostate.hex$AGE[100])
expect_equal(prostate.hex$baz[100], prostate.hex$AGE[100])


# local assignment of existing column
prostate.local$PSA = prostate.local$AGE
prostate.local[,"VOL"] = prostate.local$AGE
prostate.local["GLEASON"] = prostate.local$AGE

expect_equal(prostate.local$PSA[100], prostate.local$AGE[100])
expect_equal(prostate.local$VOL[100], prostate.local$AGE[100])
expect_equal(prostate.local$GLEASON[100], prostate.local$AGE[100])


# remote assignment of existing column
prostate.hex$PSA = prostate.hex$AGE
prostate.hex[,"VOL"] = prostate.hex$AGE
prostate.hex["GLEASON"] = prostate.hex$AGE

expect_equal(prostate.hex$PSA[100], prostate.hex$AGE[100])
expect_equal(prostate.hex$VOL[100], prostate.hex$AGE[100])
expect_equal(prostate.hex$GLEASON[100], prostate.hex$AGE[100])


testEnd()

}

doTest("PUB-636 we don't support certain kinds of column references for assignments, both creating and setting.", test.pub_636_column_references)

