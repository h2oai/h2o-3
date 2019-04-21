setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.calibration.binomial <- function() {

    ecology.hex <- h2o.importFile(locate("smalldata/gbm_test/ecology_model.csv"))
    ecology.hex$Angaus <- as.factor(ecology.hex$Angaus)

    ecology.split <- h2o.splitFrame(ecology.hex, seed = 12354)
    ecology.train <- ecology.split[[1]]
    ecology.calib <- ecology.split[[2]]

    # introduce a weight column (artificial non-constant) ONLY to the train set (NOT the calibration one)
    weights <- c(0, rep(1, nrow(ecology.train) - 1))
    ecology.train$weight <- as.h2o(weights)

    # Train H2O XGBoost Model with Calibration
    ecology.model <- h2o.xgboost(x = 3:13, y = "Angaus", training_frame = ecology.train,
                             ntrees = 10,
                             max_depth = 5,
                             min_rows = 10,
                             learn_rate = 0.1,
                             distribution = "multinomial",
                             weights_column = "weight",
                             calibrate_model = TRUE,
                             calibration_frame = ecology.calib
    )

    predicted <- h2o.predict(ecology.model, ecology.calib)

    # Check that calibrated probabilities were appended to the output frame
    expect_equal(colnames(predicted), c("predict", "p0", "p1", "cal_p0", "cal_p1"))

    # Manually scale the probabilities using GLM in R
    manual.calib.input <- cbind(as.data.frame(predicted$p1), as.data.frame(ecology.calib$Angaus))
    colnames(manual.calib.input) <- c("x", "y")
    manual.calib.model <- glm(y ~ x, manual.calib.input, family = binomial)
    manual.calib.predicted <- predict(manual.calib.model, newdata = manual.calib.input, type = "response")

    # Check that manually calculated probabilities match output from H2O
    expect_equal(
      as.data.frame(predicted$cal_p1),
      data.frame(cal_p1 = manual.calib.predicted, row.names = NULL),
      tolerance = 1e-4, scale = 1
    )
}

doTest("XGBoost: Test Binomial Model Calibration (Platt Scaling)", test.XGBoost.calibration.binomial)

