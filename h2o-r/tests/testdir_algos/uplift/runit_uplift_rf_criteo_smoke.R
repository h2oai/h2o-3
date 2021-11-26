setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.UplitRandomForest.smoke <- function() {
    train <- h2o.importFile(locate("smalldata/uplift/upliftml_train.csv"))
    train$treatment <- as.factor(train$treatment)
    train$outcome <- as.factor(train$outcome)

    uplift.model <- h2o.upliftRandomForest(
                                             training_frame = train,
                                             x = sprintf("feature_%s",seq(0:11)),
                                             y = "outcome",
                                             ntrees=10,
                                             max_depth=10,
                                             treatment_column="treatment",
                                             uplift_metric="AUTO",
                                             distribution="AUTO",
                                             gainslift_bins=10,
                                             min_rows=10,
                                             nbins=1000,
                                             seed=1234,
                                             sample_rate=0.5,
                                             auuc_type="AUTO"
                                          )
    score <- h2o.predict(uplift.model, train)
    mean.score <- mean(score$uplift_predict)
    print(mean.score)
    print(score)
    expect_equal(0.0767, mean.score, tolerance = 0.01, scale = 1)
    perf <- h2o.performance(uplift.model, train)
    auuc <-h2o.auuc(perf)
    print(auuc)
    expect_equal(251.5781, auuc, tolerance = 0.01, scale = 1)
    plot(perf)
    print("Print auuc table")
    auuc.table <- h2o.auuc_table(perf)
    print(auuc.table)
}

doTest("UpliftRandomForest: Smoke Test", test.UplitRandomForest.smoke)
