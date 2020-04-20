setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.psvm_segment <- function() {
    iris_hex <- h2o.importFile(locate("smalldata/junit/iris.csv"))
    iris_hex$petal_wid_ind <- as.factor(iris_hex$petal_wid < median(iris_hex$petal_wid))
    iris_hex$petal_wid <- NULL

    models <- h2o.train_segments(algorithm="psvm", y="petal_wid_ind", training_frame=iris_hex, segment_columns="class")

    models_df <- as.data.frame(models)
    expect_equal(3, nrow(models_df))
}

doTest("PSVM Test: Segment Model Building", check.psvm_segment)

