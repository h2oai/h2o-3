setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.kmeans_segment <- function() {
    iris_hex <- h2o.importFile(locate("smalldata/junit/iris.csv"))

    models <- h2o.train_segments(algorithm="kmeans", training_frame=iris_hex, segment_columns="class")

    models_df <- as.data.frame(models)
    expect_equal(3, nrow(models_df))
    expect_equal("SUCCEEDED", unique(as.character(models_df$status)))
}

doTest("k-means Test: Segment Model Building", check.kmeans_segment)
