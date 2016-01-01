setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.runit_NOPASS_pub_532_negative_exponent <- function() {

covtype.hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/covtype/covtype.20k.data")), "cov")

# Are we in the right universe?
expect_equal(20000, dim(covtype.hex)[1])
expect_equal(55, dim(covtype.hex)[2])

# currently works:
covtype.hex$C55^2
covtype.hex$C55^(-2)

# currently fails:
val0 <- covtype.hex$C55^-2
covtype.hex$C55^ -2
covtype.hex$C55 ^-2
covtype.hex$C55 ^-2 


#w/o h2o.exec:
val <- covtype.hex$C55^-2
covtype.hex$C55^ -2
covtype.hex$C55 ^-2
covtype.hex$C55 ^-2 

print(val0)

print("================")

print(val)

print(tail(val))




}

h2oTest.doTest("PUB-532 expresison parser fails on negative exponents.", test.runit_NOPASS_pub_532_negative_exponent)

