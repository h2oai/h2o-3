setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.segment_gbm <- function() {
    smtrees <- h2o.importFile(locate("smalldata/gbm_test/smtrees.csv"))
    smtrees$segment <- as.factor(smtrees$C1 < 6)

    segment_models <- h2o.train_segments(x=c("girth", "height"), y="vol", ntrees=3, max_depth=1, seed=42,
                                         distribution="gaussian", min_rows=2,
                                         training_frame=smtrees, segment_columns="segment", segment_models_id = "smtrees_sm",
                                         algorithm="gbm")

    expect_equal(segment_models@segment_models_id, "smtrees_sm")

    segment_models_df <- as.data.frame(segment_models)
    print(segment_models_df)
    expect_equal(nrow(segment_models_df), 2)
    for (model_id in segment_models_df$Model) {
        expect_equal(h2o.getModel(model_id)@model_id, model_id)
    }
}

doTest("Segment model building with GBM", test.segment_gbm)
