setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub_474_h2o.ddply_should_return_void_for_void_functions <- function() {

covtype.hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/covtype/covtype.20k.data")), "cov")
covtype.local <- as.data.frame(covtype.hex)

# Are we in the right universe?
expect_equal(20000, dim(covtype.hex)[1])
expect_equal(55, dim(covtype.hex)[2])

# in R h2o.ddply returns a dimension-0 result:
zzz = function(df) {}
d = plyr::ddply(covtype.local, plyr::.(C1), zzz)
expect_equal(0, dim(d)[1])
expect_equal(0, dim(d)[2])

# currently fails:
d <- head(h2o.ddply(covtype.hex, c(1), function(x) {}))
expect_equal(0, dim(d)[1])
expect_equal(0, dim(d)[2])



}

h2oTest.doTest("PUB-474 h2o.ddply should return void for void functions.", test.pub_474_h2o.ddply_should_return_void_for_void_functions)

