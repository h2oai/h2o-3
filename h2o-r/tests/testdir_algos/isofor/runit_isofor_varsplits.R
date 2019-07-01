setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.IsolationForest.varsplits <- function() {
    prostate_hex <- h2o.importFile(locate('smalldata/testng/prostate.csv'))

    model <- h2o.isolationForest(training_frame = prostate_hex)

    varsplits <- as.data.frame(h2o.varsplits(model))
    
    expect_equal(colnames(varsplits), c("variable", "count", "aggregated_split_ratios", "aggregated_split_depths"))
    expect_equal(colnames(prostate_hex), varsplits$variable)
}

doTest("IsolationForest: Test Variable Splits", test.IsolationForest.varsplits)
