setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.psvm.smoke <- function() {
    splice <- h2o.importFile(locate('smalldata/splice/splice.svm'))

    svm_model <- h2o.psvm(gamma = 0.01, rank_ratio = 0.1, y = "C1",
                          training_frame = splice, disable_training_metrics = FALSE)
    print(svm_model)

    pred <- h2o.predict(svm_model, splice)
    expect_equal(nrow(splice), nrow(pred))
    
    accuracy <- h2o.accuracy(h2o.performance(svm_model))[0]$accuracy
    printf("Accuracy (on train): %s", accuracy)
}

doTest("PSVM: Smoke Test", test.psvm.smoke)
