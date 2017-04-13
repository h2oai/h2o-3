setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.loco.regression <- function(){
    iris.hex <- as.h2o(iris)
    iris.hex[,5] = as.factor(iris.hex[,5])

    #Build GBM (Regression)
    gbm <- h2o.gbm(c(1:3,5),4,iris.hex)

    #GBM LOCO (Regression)
    h2o_loco <- h2o.loco(gbm,iris.hex)

    #R version of LOCO
    r_loco <- run_loco_r(gbm,iris.hex)

    expect_equal(as.data.frame(h2o_loco),r_loco)


}

doTest("LOCO Test", test.loco.regression)