setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.interactions_valid_nums <- function() {
    N <- 10000
    X <- runif(n = N)
    Y <- runif(n = N)
    df <- data.frame(x = X, y = Y, x_y <- X * Y, resp = 0.2 * X + 0.5 * Y + 0.3 * X * Y)
    df.hex <- as.h2o(df)
    df.expanded.hex <- h2o.cbind(.getExpanded(df.hex[, c("x", "y")], interactions = c("x", "y"), T, F, T), df.hex)

    df.split <- h2o.splitFrame(df.expanded.hex, 0.8, seed = 1234)
    train <- df.split[[1]]
    test <- df.split[[2]]

    m1 <- h2o.glm(x = c("x", "y"), y = "resp",
    interactions = c("x", "y"),
    training_frame = train[, c("x", "y", "resp")], validation_frame = test[, c("x", "y", "resp")], seed = 1234)

    m2 <- h2o.glm(x = c("x", "y", "x_y"), y = "resp",
    training_frame = train, validation_frame = test, seed = 1234)

    expect_equal(m1@model$coefficients, m2@model$coefficients)
}

doTest("Test GLM numeric interactions with a validation frame", test.glm.interactions_valid_nums)