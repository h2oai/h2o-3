setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



knn.smoke <- function() {
    iris.hex <- h2o.uploadFile( locate("smalldata/iris/iris.csv"))
    iris.knn <-  h2o.knn(x=1:4, y=5, training_frame=iris.hex, k=3 , distance="euclidean", seed=1234)

    # Score test data with different default auc_type (previous was "NONE", so no AUC calculation)
    perf <- h2o.performance(iris.knn, test.hex, auc_type="WEIGHTED_OVO")

    # Check default AUC is set correctly
    auc_table <- h2o.multinomial_auc_table(perf)
    default_auc <- h2o.auc(perf)
    weighted_ovo_auc <- auc_table[32, 4] # weighted ovo AUC is the last number in the table

    expect_equal(default_auc, weighted_ovo_auc)
    
    distances <- iris.knn@model$distances
    print(distances)
}

doTest("KNN Test: Check model is running.", knn.smoke)
