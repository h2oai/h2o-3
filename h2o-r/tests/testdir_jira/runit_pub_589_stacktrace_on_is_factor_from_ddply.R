setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub_589_stacktrace_on_is_factor_from_ddply <- function() {

covtype.hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/covtype/covtype.20k.data")), "cov")

# covtype.local = as.data.frame(covtype.hex)

# Are we in the right universe?
expect_equal(20000, dim(covtype.hex)[1])
expect_equal(55, dim(covtype.hex)[2])

###########################################################
# execs
###########################################################

m <- function(x) { mean( x[,2]) }
h2o.ddply(covtype.hex, c(2), m)

s <- function(x) { sd( x[,2]) }
h2o.ddply(covtype.hex, c(2), s)

h2o.ddply(covtype.hex, c(2), nrow)
h2o.ddply(covtype.hex, c(2), ncol)
h2o.ddply(covtype.hex, c(2), length)
h2o.ddply(covtype.hex, c(2), function(x) { is.factor(x[,1]) })



}

h2oTest.doTest("PUB-589 NPE when calling is.factor from ddply.", test.pub_589_stacktrace_on_is_factor_from_ddply)

