setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.glm_segment <- function() {
    iris_hex <- h2o.importFile(locate("smalldata/junit/iris.csv"))

    models <- h2o.train_segments(algorithm="glm",
                                 y="petal_wid", training_frame=iris_hex, segment_columns="class")

    models_df <- as.data.frame(models)
    expect_equal(3, nrow(models_df))
    expect_equal("SUCCEEDED", unique(as.character(models_df$status)))
}

doTest("GLM Test: Segment Model Building", check.glm_segment)
