setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_5108 <- function() {
    col1 <- rep(0, 10000)
    col1[seq(1000) * 10] <- runif(1000)
    col2 <- runif(10000) # noise (beta for col2 should be 0)
    col3 <- -col1
    col4 <- rep(0, 10000)
    col4[seq(1000) * 10] <- 1e-05 # sparse noise (this column triggers sparse treatment)
    resp <- 0.9 * col1 + 0.5 * col3

    train <- data.frame(col1, col2, col3, col4, resp)
    train.hex <- as.h2o(train)

    glm.model <- h2o.glm(y = "resp", training_frame = train.hex, standardize = FALSE, seed = 1233)

    # Predict with a bad response (NA)
    test.bad.resp <- data.frame(col1, col2, col3, col4, resp = NA)
    test.bad.resp.hex <- as.h2o(test.bad.resp)
    preds.bad.resp.hex <- h2o.predict(glm.model, test.bad.resp.hex)

    # Predict without a response column
    test.no.resp <- data.frame(col1, col2, col3, col4)
    test.no.resp.hex <- as.h2o(test.no.resp)
    preds.no.resp.hex <- h2o.predict(glm.model, test.no.resp.hex)

    expect_equal(preds.no.resp.hex, preds.bad.resp.hex)
}

doTest("PUBDEV-5108: Bad response column causes exception in GLM predict", test.pubdev_5108)