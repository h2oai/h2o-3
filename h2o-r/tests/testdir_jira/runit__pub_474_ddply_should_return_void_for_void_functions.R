setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.pub_474_ddply_should_return_void_for_void_functions <- function(localH2O) {

covtype.hex <- h2o.importFile(localH2O, normalizePath(locate("smalldata/covtype/covtype.20k.data")), "cov")
covtype.local <- as.data.frame(covtype.hex)

# Are we in the right universe?
expect_equal(20000, dim(covtype.hex)[1])
expect_equal(55, dim(covtype.hex)[2])

# in R ddply returns a dimension-0 result:
zzz = function(df) {}
d = plyr::ddply(covtype.local, plyr::.(C1), zzz)
expect_equal(0, dim(d)[1])
expect_equal(0, dim(d)[2])

# currently fails:
h2o.addFunction(object = localH2O, fun = zzz, name = "zzz" )
d <- head(h2o.ddply(covtype.hex, c(1), function(x) {}))
expect_equal(0, dim(d)[1])
expect_equal(0, dim(d)[2])

testEnd()

}

doTest("PUB-474 ddply should return void for void functions.", test.pub_474_ddply_should_return_void_for_void_functions)

