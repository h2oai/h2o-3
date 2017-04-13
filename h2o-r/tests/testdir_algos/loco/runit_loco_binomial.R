setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.loco.binomial <- function(){
    pros.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"))

    #Sample data
    pros.hex = pros.hex[1:150,]
    pros.hex[,2] = as.factor(pros.hex[,2])

    #Build GBM (Binomial)
    gbm <- h2o.gbm(3:9,2,pros.hex)

    #GBM LOCO (Binomial)
    h2o_loco <- h2o.loco(gbm,pros.hex)

    #R version of LOCO
    r_loco <- run_loco_r(gbm,pros.hex,regression=FALSE)

    expect_equal(as.data.frame(h2o_loco),r_loco)

}

doTest("LOCO Test", test.loco.binomial)