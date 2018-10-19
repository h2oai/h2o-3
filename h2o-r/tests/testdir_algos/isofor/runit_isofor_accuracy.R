setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.IsolationForest.accuracy <- function() {
    set.seed(1234)
    N = 1e6
    random_data <- data.frame(
        x = c(rnorm(N, 0, 0.5), rnorm(N*0.05, -2, 1)),
        y = c(rnorm(N, 0, 0.5), rnorm(N*0.05,  2, 1)),
        outlier = c(rep("NO", N), rep("YES", (0.05*N)))
    )
    random_data.hex <- as.h2o(random_data)

    h2o_isolation_forest <- h2o.isolationForest(x = c("x", "y"), training_frame = random_data.hex[, c("x", "y")],
                                                ntrees = 100, seed = 1234, build_tree_one_node = TRUE)

    anomaly_score <- h2o.predict(h2o_isolation_forest, random_data.hex)

    glm_train <- anomaly_score
    glm_train$outlier <- random_data.hex$outlier

    glm_model <- h2o.glm(training_frame = glm_train, x = "predict", y = "outlier", family = "binomial")

    # we should get a good AUC
    auc <- h2o.auc(glm_model)
    cat(sprintf("AUC: %f\n", auc))
    expect_equal(h2o.auc(glm_model), 0.97, tolerance = .01, scale = 1)
    # rate of outlier misclassification should be low
    cm <- h2o.confusionMatrix(glm_model)
    print(cm)
    expect_equal(cm["YES", "Error"], 0.14, tolerance = .05, scale = 1)
}

doTest("IsolationForest: Test Accuracy", test.IsolationForest.accuracy)
