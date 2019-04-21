setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.5029 <- function() {
    # Create a dummy training frame
    rp <- head(c(seq(10), 10 + (seq(1000)*seq(1000))), 1000)
    resp <- rep("A", 1000000)
    resp[rp] <- "B"
    C1 <- rep(0, 1000000)
    C1[rp] <- rp
    C2 <- rep(0, 1000000)
    C2[rp] <- rp - 1
    df.train <- as.h2o(data.frame(
        C1 = C1,
        C2 = C2,
        resp = as.factor(resp)
    ))

    # Build a GLM model
    glm.test <- h2o.glm(training_frame = df.train, y="resp", family = "binomial",
                        missing_values_handling = "MeanImputation", seed = 1000000)

    # Create a sparse test frame
    C1 <- rep(NA, 1000000)
    C1[rp] <- 1
    df.test <- as.h2o(data.frame(C1 = C1, C2 = NA)) # The purpose of the second column is to meet the sparsity criteria

    dense.size <- 15

    pred.dense <- as.data.frame(h2o.predict(glm.test, df.test[1:dense.size,]))
    pred.dense$predict <- as.character(pred.dense$predict)

    pred.sparse <- as.data.frame(h2o.predict(glm.test, df.test))
    pred.sparse$predict <- as.character(pred.sparse$predict)

    expect_equal(pred.dense, head(pred.sparse, dense.size))
}

doTest("PUBDEV-5096: GLM doesn't correctly impute missing values on sparse datasets", test.pubdev.5029)
