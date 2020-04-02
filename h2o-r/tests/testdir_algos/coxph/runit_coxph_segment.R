setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.segment <- function() {
    ovarian.hex <- as.h2o(ovarian)
    ovarian.hex$rx <- as.factor(ovarian.hex$rx)

    models <- h2o.train_segments(algorithm="coxph",
                                 stop_column = "futime", event_column = "fustat", x = c("age", "rx"),
                                 segment_columns="rx", training_frame = ovarian.hex)
    models_df <- as.data.frame(models)
    expect_equal(2, nrow(models_df))
    expect_equal("SUCCEEDED", unique(as.character(models_df$status)))
}

doTest("CoxPH: Test Segment Model Building", test.CoxPH.segment)
