setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.interactions_pubdev5277 <- function() {
    df <- h2o.importFile(locate("smalldata/airlines/allyears2k_headers.zip"))
    df[is.na(df$Distance), "Distance"] <- h2o.mean(df$Distance, na.rm = TRUE)
    df[is.na(df$CRSElapsedTime), "CRSElapsedTime"] <- h2o.mean(df$CRSElapsedTime, na.rm = TRUE)

    XY <- names(df)[c(1,2,3,4,6,8,9,13,17,19,31)]
    interactions <- XY[c(7,9)]

    # Build a GLM model with implicit interactions
    m1 <- h2o.glm(x = XY[-length(XY)], y = XY[length(XY)],
                  training_frame = df[, XY],
                  interactions = interactions,
                  lambda_search = TRUE, family="binomial", solver = "IRLSM",
                  early_stopping = FALSE, seed = 1234)

    # Build a GLM model using explicit interactions
    df.expanded <- h2o.cbind(.getExpanded(df[,c(XY)], interactions = c(interactions), T, F, F), df$IsDepDelayed)
    XY.expanded <- c(setdiff(m1@model$coefficients_table$names, "Intercept"), "IsDepDelayed")
    m2 <- h2o.glm(x = 1:(length(XY.expanded)-1), y = length(XY.expanded),
                  training_frame = df.expanded[, XY.expanded],
                  lambda_search = TRUE, family="binomial", solver = "IRLSM",
                  early_stopping = FALSE, seed = 1234)

    # Performance is similar
    print("comparing AUC of m1 and m2")
    print(m1@model$training_metrics@metrics$AUC- m2@model$training_metrics@metrics$AUC)
    expect_equal(m1@model$training_metrics@metrics$AUC, m2@model$training_metrics@metrics$AUC, tolerance = 0.001, scale = 1)
    print("Comparing logloss of m1 and m2")
    print(m1@model$training_metrics@metrics$logloss-m2@model$training_metrics@metrics$logloss)
    expect_equal(m1@model$training_metrics@metrics$logloss, m2@model$training_metrics@metrics$logloss, tolerance = 0.001, scale = 1)

    # Coefficients of numeric features from the orginal dataset are reasonably similar in both models
    simple.coef <- setdiff(m1@model$coefficients_table$names[grep('\\.', m1@model$coefficients_table$names, invert = TRUE)], "Intercept")
    print("Comparing intercept of m1 and m2")
    print(m1@model$coefficients["Intercept"]-m2@model$coefficients["Intercept"])
    expect_equal(m1@model$coefficients["Intercept"], m2@model$coefficients["Intercept"], tolerance = 0.01, scale = m1@model$coefficients["Intercept"])
    print("Comparing coefficients of m1 and m2")
    print(m1@model$coefficients[simple.coef]-m2@model$coefficients[simple.coef])
    expect_equal(m1@model$coefficients[simple.coef], m2@model$coefficients[simple.coef], tolerance = 5e-4, scale = 1)

    # Coefficients for interactions don't match (one example:) - needs to be fixed in PUBDEV-5277
    print("Comparing coefficients of m1 and 0.346")
    print(abs(0.346 - m1@model$coefficients["UniqueCarrier_Origin.AA_ABQ"]))
    expect_true(abs(0.346 - m1@model$coefficients["UniqueCarrier_Origin.AA_ABQ"]) < 0.01)
    print("Comparing coefficients of m2 and 7.456")
    print(abs(7.456 - m2@model$coefficients["UniqueCarrier_Origin.AA_ABQ"]))
    expect_true(abs(7.456 - m2@model$coefficients["UniqueCarrier_Origin.AA_ABQ"]) < 0.1)
}

doTest("Demonstrates that 2 GLM runs with implicit interactions and explicit interactions differ", test.glm.interactions_pubdev5277)