setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.loco.multinomial <- function(){
    iris.hex <- as.h2o(iris)

    #Build GBM (Multinomial)
    gbm <- h2o.gbm(1:4,5,iris.hex)

    #GBM LOCO (Multinomial)
    h2o_loco <- h2o.loco(gbm,iris.hex)

    #R version of LOCO
    r_loco <- run_loco_r_mult(gbm,iris.hex)
    expect_equal(as.data.frame(h2o_loco),r_loco)

}

doTest("LOCO Test", test.loco.multinomial)