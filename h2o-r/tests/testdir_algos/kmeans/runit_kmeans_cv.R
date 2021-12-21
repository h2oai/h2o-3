setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



# Test k-means clustering on iris.csv with CV enabled - cv centroid stats are not available for CV metric
test.km.cv <- function() {
    data <- h2o.importFile( locate("smalldata/iris/iris.csv"))
    model <- h2o.kmeans(training_frame = data, nfolds=3, estimate_k=TRUE, x = colnames(data)[-1])
    
    print(model)
 
    tm <- model@model$training_metrics@metrics$centroid_stats
    print(tm)
    expect_false(is.null(tm))
    
    cv <- model@model$cross_validation_metrics@metrics$centroid_stats
    print(cv)
    expect_true(is.null(cv))   
}

doTest("KMeans Test: Cross-validation", test.km.cv)
