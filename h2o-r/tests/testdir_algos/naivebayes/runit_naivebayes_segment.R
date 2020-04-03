setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.naivebayes_segment <- function() {
    iris_hex <- h2o.importFile(locate("smalldata/junit/iris.csv"))

    models <- h2o.train_segments(algorithm="naiveBayes", y="petal_wid", training_frame=iris_hex, segment_columns="class")

    models_df <- as.data.frame(models)
    expect_equal(3, nrow(models_df))
}

doTest("Naive Bayes Test: Segment Model Building", check.naivebayes_segment)

