setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.UplitRandomForest.smoke <- function() {
    train <- h2o.importFile(locate("smalldata/uplift/criteo_uplift_13k.csv"))
    train$treatment <- as.factor(train$treatment)
    train$conversion <- as.factor(train$conversion)

    uplift.model <- h2o.upliftRandomForest(
                                             training_frame = train,
                                             x = sprintf("f%s",seq(0:10)),
                                             y = "conversion",
                                             ntrees=10,
                                             max_depth=5,
                                             treatment_column="treatment",
                                             uplift_metric="AUTO",
                                             distribution="AUTO",
                                             gainslift_bins=10,
                                             min_rows=10,
                                             nbins=1000,
                                             seed=1234,
                                             sample_rate=0.99,
                                             auuc_type="AUTO"
                                          )
    score <- h2o.predict(uplift.model, train)

    expect_equal(0.09, mean(score$uplift_predict), tolerance = 0.01, scale = 1)
}

doTest("UpliftRandomForest: Smoke Test", test.UplitRandomForest.smoke)
