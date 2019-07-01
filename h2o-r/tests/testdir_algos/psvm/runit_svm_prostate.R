setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.psvm.prostate <- function() {
    prostate <- h2o.importFile(locate('smalldata/testng/prostate_train.csv'))
    prostate$CAPSULE <- as.factor(prostate$CAPSULE)

    prostate_test <- h2o.importFile(locate('smalldata/testng/prostate_test.csv'))
    prostate_test$CAPSULE <- as.factor(prostate_test$CAPSULE)

    svm_model <- h2o.psvm(y = "CAPSULE", training_frame = prostate, validation_frame = prostate_test)

    accuracy <- h2o.accuracy(h2o.performance(svm_model, valid=TRUE))[1, "accuracy"]
    print(sprintf("Accuracy (on test): %s", accuracy))

    adapt <- function(f) {
        f$CAPSULE <- as.factor(f$CAPSULE)
        h2o.scale(f)
    }
    
    prostate_cat <- h2o.importFile(locate('smalldata/glm_test/prostate_cat_train.csv'))
    prostate_cat <- adapt(prostate_cat)

    prostate_cat_test <- h2o.importFile(locate('smalldata/glm_test/prostate_cat_test.csv'))
    prostate_cat_test <- adapt(prostate_cat_test)

    svm_model_cat <- h2o.psvm(y = "CAPSULE", training_frame = prostate_cat, validation_frame = prostate_cat_test,
                              x = c("AGE", "RACE", "DPROS", "DCAPS", "PSA", "GLEASON"))

    accuracy_cat <- h2o.accuracy(h2o.performance(svm_model_cat, valid=TRUE))[1, "accuracy"]
    print(sprintf("Accuracy (on test): %s", accuracy_cat))

    # check that accuracy is not too far off when comparing models trained on numerical-only data and mix of both categorical and numerical
    exp_accuracy <- (accuracy + accuracy_cat) / 2
    expect_equal(exp_accuracy, accuracy, tolerance = 0.05)
    expect_equal(exp_accuracy, accuracy_cat, tolerance = 0.05)
}

doTest("PSVM: Prostate Test - compare SVM on numerical and categorical data", test.psvm.prostate)
