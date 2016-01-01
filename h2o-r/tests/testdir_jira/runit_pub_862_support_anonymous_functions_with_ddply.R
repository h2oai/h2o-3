setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub_862_support_anonymous_functions_with_ddply <- function() {

covtype.hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/covtype/covtype.20k.data")), "cov")

covtype.local <- as.data.frame(covtype.hex)

# Are we in the right universe?
expect_equal(20000, dim(covtype.local)[1])
expect_equal(55, dim(covtype.local)[2])

###########################################################
# execs
###########################################################

h2o.ddply(covtype.hex, c(2), function(x) { mean( x[,2]) })



}

h2oTest.doTest("PUB-862 ddply should support anonymous functions.", test.pub_862_support_anonymous_functions_with_ddply)

