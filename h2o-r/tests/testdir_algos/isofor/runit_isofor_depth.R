setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.IsolationForest.depth <- function() {
    set.seed(1234)
    N = 1e6
    random_data <- data.frame(
        x = c(rnorm(N, 0, 0.5), rnorm(N*0.05, -1.5, 1)),
        y = c(rnorm(N, 0, 0.5), rnorm(N*0.05,  1.5, 1)),
        outlier = c(rep("NO", N), rep("YES", (0.05*N)))
    )
    random_data.hex <- as.h2o(random_data)

    isofor.model <- h2o.isolationForest(training_frame = random_data.hex, seed = 1234)


    sample_ind <- sample(c(1:nrow(random_data)), size = 1000)
    sample_ind <- sample_ind[order(sample_ind)]

    # calculated score
    score <- h2o.predict(isofor.model, random_data.hex[sample_ind, ])

    # manually calculate score from average depth
    depths <- h2o.nchar(h2o.predict_leaf_node_assignment(isofor.model, random_data.hex[sample_ind, ]))
    avg_path_length <- h2o.mean(depths, axis = 1, return_frame = TRUE)

    normalize <- function(avg_path_length) {
        min_length <- min(avg_path_length)
        max_length <- max(avg_path_length)
        as.data.frame((max_length - avg_path_length) / (max_length - min_length))[, 1]
    }
    result_pred <- normalize(score)
    result_manual <- normalize(avg_path_length$mean)

    expect_equal(result_pred, result_manual)

}

doTest("IsolationForest: Test Depth Calculation", test.IsolationForest.depth)