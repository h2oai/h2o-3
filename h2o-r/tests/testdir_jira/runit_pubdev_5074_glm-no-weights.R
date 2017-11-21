setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.5074 <- function() {
    library(Matrix)

    set.seed(17)

    M <- rsparsematrix(300, 19999, nnz = 28218)
    Ms <- rowSums(M)

    train <- as.data.frame(as.matrix(M))
    train$label <- as.factor(Ms > median(Ms))
    train$weight <- runif(nrow(train))

    train.hex <- as.h2o(train)
    gg <- h2o.glm(x = 1:19999, y = "label", training_frame = train.hex, weights_column = "weight", family = "binomial")

    test.hex <- train.hex[,1:19999]

    preds <- h2o.predict(gg, test.hex)

    expect_equal(0, sum(is.na(preds$predict)))
}

doTest("PUBDEV-5074: GLM predict produces NAs if weight column is not specified", test.pubdev.5074)
