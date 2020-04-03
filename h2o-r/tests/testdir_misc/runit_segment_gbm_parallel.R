setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.segment_gbm_parallel <- function() {
    smtrees <- h2o.importFile(locate("smalldata/gbm_test/smtrees.csv"))
    smtrees$segment <- as.factor(smtrees$C1 < 6)

    segment_models <- h2o.train_segments(algorithm="gbm",
                                         x=c("girth", "height"), y="vol", ntrees=3, max_depth=1, seed=42,
                                         distribution="gaussian", min_rows=2,
                                         training_frame=smtrees, segment_columns="segment", segment_models_id = "smtrees_sm",
                                         parallelism=2)

    segment_models_df <- as.data.frame(segment_models)
    expect_equal(nrow(segment_models_df), 2)
}

doTest("Segment model building with GBM (parallelism enabled)", test.segment_gbm_parallel)
