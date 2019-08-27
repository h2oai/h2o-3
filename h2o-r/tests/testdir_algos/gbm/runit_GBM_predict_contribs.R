setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.contribs_vs_xgb <- function() {
    expect_true(require("xgboost"))

    prostate_hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate_hex <- prostate_hex[, c("VOL", "DPROS", "AGE")]
    prostate_r <- as.data.frame(prostate_hex)

    prostate_matrix <- as.matrix(prostate_r[c(1:2)])

    gbm_model <- h2o.gbm(training_frame = prostate_hex, y = "AGE",
    max_depth = 5, learn_rate = 1.0, seed = 42, ntree = 1)

    xgb_model <- xgboost(data = prostate_matrix, label = prostate_r$AGE, max_depth = 5,
    eta = 1.0, nrounds = 2, objective = "reg:linear", seed = 42)

    test_dmatrix <- xgb.DMatrix(prostate_matrix, label=prostate_r$AGE)

    # Variable importance shouldn't be too far off
    xgb_imp <- xgb.importance(model = xgb_model)
    print(xgb_imp)
    gbm_imp <- h2o.varimp(gbm_model)
    print(gbm_imp)

    # Compare the contributions
    xgb_contribs <- predict(xgb_model, newdata = test_dmatrix, predcontrib = TRUE)
    head(xgb_contribs)

    gbm_contribs <- as.matrix(h2o.predict_contributions(gbm_model, prostate_hex))
    head(gbm_contribs)

    # We cannot expect same contributions but the models should agree (to some extent) 
    # what feature has a positive and a negative effect
    features <- c("VOL", "DPROS")
    same_sign <- sum(sign(gbm_contribs[,features]) == sign(xgb_contribs[,features]))
    same_sign_ratio <- same_sign / (length(features) * nrow(gbm_contribs))
    print(same_sign_ratio)
    expect_gte(same_sign_ratio, 0.8)
}

doTest("GBM Test: Classification with 50 categorical level predictor", test.GBM.contribs_vs_xgb)
