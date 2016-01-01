setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



rfReg.vi.test<- function() {
    data2.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/BostonHousing.csv"), destination_frame="data2.hex")
    x=1:13
    y=14
    rf <- h2o.randomForest(x, y, data2.hex, ntrees=100, max_depth=20, 
                           min_rows=100, seed=0)
    vi=match(rf@model$variable_importances[,1], colnames(data2.hex))

    expect_equal(vi[1:2], c(13,6))
    
}
h2oTest.doTest("Variable Importance RF Test: Boston Housing Smalldata", rfReg.vi.test)
