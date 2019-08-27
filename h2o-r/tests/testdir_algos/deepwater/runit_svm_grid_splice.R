setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.psvm.gridsearch <- function() {
    data <- h2o.importFile(locate('smalldata/svm_test/svmguide1.svm'))
    data$C1 <- as.factor(data$C1)
    data_test <- h2o.importFile(locate('smalldata/svm_test/svmguide1_test.svm'))
    data_test$C1 <- as.factor(data_test$C1)

    hyper_params <- list(gamma = c(0.01, 0.1, 1), hyper_param = c(0.1, 1, 10), rank_ratio = c(0.1, 0.01))
    search_criteria = list(strategy = "RandomDiscrete", max_models = 4)
    
    svm_grid <- h2o.grid("psvm", y = "C1", grid_id="svm_grid",
                         training_frame = data, validation_frame = data_test,
                         hyper_params = hyper_params, search_criteria = search_criteria, seed = 42)
    print(svm_grid)

    by_accuracy <- h2o.getGrid("svm_grid", "accuracy")
    print(by_accuracy)
}

doTest("SVM Random Hyperparameter Search Test", test.psvm.gridsearch)

