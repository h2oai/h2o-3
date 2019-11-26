setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.calibration.binomial <- function() {
    set.seed(42)

    ecology.hex <- h2o.importFile(locate("smalldata/gbm_test/ecology_model.csv"))
    ecology.hex$Angaus <- as.factor(ecology.hex$Angaus)
    ecology.hex$Weights <- as.h2o(sample(1:5, size = nrow(ecology.hex), replace = TRUE))

    ecology.split <- h2o.splitFrame(ecology.hex, seed = 12354)
    ecology.train <- ecology.split[[1]]
    ecology.calib <- ecology.split[[2]]

    # Train H2O XGBoost Model with Calibration
    ecology.model <- h2o.xgboost(x = 3:13, y = "Angaus", training_frame = ecology.train,
                                 ntrees = 10, weights_column = "Weights",
                                 calibrate_model = TRUE,
                                 calibration_frame = ecology.calib
    )

    predicted <- h2o.predict(ecology.model, ecology.train)

    # Check that calibrated probabilities were appended to the output frame
    expect_equal(colnames(predicted), c("predict", "p0", "p1", "cal_p0", "cal_p1"))

    # Manually scale the probabilities using GLM in R
    predicted.calib <- h2o.predict(ecology.model, ecology.calib)
    manual.calib.input <- cbind(as.data.frame(predicted.calib$p1), as.data.frame(ecology.calib[, c("Angaus", "Weights")]))
    colnames(manual.calib.input) <- c("p1", "response", "weights")
    manual.calib.model <- glm(response ~ p1, manual.calib.input, family = binomial, weights = weights)
    manual.calib.predicted <- predict(manual.calib.model, newdata = as.data.frame(predicted$p1), type = "response")

    # Check that manually calculated probabilities match output from H2O
    expect_equal(
      as.data.frame(predicted$cal_p1),
      data.frame(cal_p1 = manual.calib.predicted, row.names = NULL),
      tolerance = 1e-4, scale = 1
    )
}

doTest("XGBoost: Test Binomial Model Calibration (Platt Scaling)", test.XGBoost.calibration.binomial)

