setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.model.gam <- function() {
    # train <- h2o.createFrame(cols = 5, seed = 22, seed_for_column_types = 55, factors = 3, missing_fraction = 0)
    # train$fold <- h2o.kfold_column(train, nfolds = 3, seed = 11)
    # train$response <- 50 + 
    #     ifelse(train$C5 == "c4.l0", 10,
    #            ifelse(train$C5 == "c4.l1", 15,
    #                   ifelse(train$C5 == "c4.12", 20, 25))) +
    #     0.2 * train$C1 +
    #     - 0.05 * train$C2 +
    #     -0.2 * train$C4 - 0.005 * train$C4^2 + 0.00005 * train$C4^3 +
    #     5*h2o.runif(train)
    train <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/gam_test/pubdev_8681_gam_cv_pred.csv")
    
    params <- list(
        x = c("C1", "C2", "C5"),
        y = "response", 
        training_frame = train,
        lambda = 0,
        keep_gam_cols = TRUE,
        gam_columns = c("C4"),
        scale = c(.05),
        num_knots = c(5),
        spline_orders = c(3)
    )
    test <- train[-c(6)]

    # no cross validation, bs = 0 (default)
    mod <- do.call(what = "h2o.gam", args = params)
    pred1 <- h2o.predict(mod, test)

    # cross validation, bs = 0 (default)
    mod2 <- do.call(what = "h2o.gam", args = c(params, fold_column = "fold"))
    print("model coefficients")
    print(mod@model$coefficients)
    print(mod2@model$coefficients)
    #C5.c4.l1      C5.c4.l2            C1            C2 C4_0_center_0 C4_0_center_1 C4_0_center_2 C4_0_center_3     Intercept 
    #5.02292119   14.94923363    0.19996898   -0.05047989   31.24583603   36.82320054   26.17113947    0.83312981   45.84605270 
    print("model residual deviance")
    print(h2o.residual_deviance(object = mod2, train = TRUE))
    print(h2o.residual_deviance(object = mod, train = TRUE))

    pred2 <- h2o.predict(mod2, test)
    # make sure two models are the same by comparing the prediction
    compareFrames(pred1, pred2)
    expect_true(abs(h2o.residual_deviance(object = mod, train = TRUE)-h2o.residual_deviance(object = mod2, train = TRUE))<1e-6)
}

doTest("General Additive Model test: cv with fold column", test.model.gam)
