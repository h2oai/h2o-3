setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


glm_control_variables_ordinal <- function() {
    
    # problematic data
    df <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")

    df$C1 <- as.factor(df$C1)
    df$C2 <- as.factor(df$C2)
    df$C3 <- as.factor(df$C3)
    df$C11 <- as.factor(df$C11)

    response <- "C11"

    ordinal_glm <- h2o.glm(family = "ordinal",
                           y = response,
                           training_frame = df,
                           generate_scoring_history = T,
                           score_each_iteration = T,
                           seed=42
    )
    
    summary(ordinal_glm)


    ordinal_glm_cv <- h2o.glm(family = "ordinal",
                              y = response,
                              training_frame = df,
                              control_variables = c("C2"),
                              generate_scoring_history = T,
                              score_each_iteration = T, 
                              seed=42
    )

    summary(ordinal_glm_cv)

    # test make unrestricted model
    unrestricted_ordinal_glm <- h2o.make_unrestricted_glm_model(ordinal_glm_cv)
    expect_false(is.null(unrestricted_ordinal_glm))
    summary(unrestricted_ordinal_glm)

    # should pass
    h2o.learning_curve_plot(ordinal_glm_cv)
    h2o.learning_curve_plot(unrestricted_ordinal_glm)

    # explain does not work for ordinal distribution - fail with partial dependence plot
    # h2o.explain(prostate_glm, df)
    # h2o.explain(unrestricted_prostate_glm, df)

    # test variable importance
    varimp <- h2o.varimp(ordinal_glm)
    varimp_cv <- h2o.varimp(ordinal_glm_cv)
    varimp_unrestricted <- h2o.varimp(unrestricted_ordinal_glm)

    # the values should be different
    expect_false(all(varimp_cv[,2] == varimp_unrestricted[,2]))
    expect_true(all(varimp[,2] == varimp_unrestricted[,2]))

    # the control variables should have zero importance
    expect_true(varimp_cv[varimp_cv$variable == "C2.1", 2] == 0)

    # in unrestricted model control variables can have larger importance than zero
    expect_true(varimp_unrestricted[varimp_unrestricted$variable == "C2.1", 2] > 0)

    
    # ordinal data
    df <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_ordinal_logit/ordinal_multinomial_training_set_small.csv")
    df$C26 <- as.factor(df$C26)
    response <- "C26"

    ordinal_glm <- h2o.glm(family = "ordinal",
                           y = response,
                           training_frame = df,
                           generate_scoring_history = T,
                           score_each_iteration = T,
                           seed=42
    )

    summary(ordinal_glm)


    ordinal_glm_cv <- h2o.glm(family = "ordinal",
                              y = response,
                              training_frame = df,
                              control_variables = c("C2"),
                              generate_scoring_history = T,
                              score_each_iteration = T,
                              seed=42
    )

    summary(ordinal_glm_cv)

    # test make unrestricted model
    unrestricted_ordinal_glm <- h2o.make_unrestricted_glm_model(ordinal_glm_cv)
    expect_false(is.null(unrestricted_ordinal_glm))
    summary(unrestricted_ordinal_glm)

    # should pass
    h2o.learning_curve_plot(ordinal_glm_cv)
    h2o.learning_curve_plot(unrestricted_ordinal_glm)

    # explain does not work for ordinal distribution - fail with partial dependence plot
    # h2o.explain(prostate_glm, df)
    # h2o.explain(unrestricted_prostate_glm, df)

    # test variable importance
    varimp <- h2o.varimp(ordinal_glm)
    varimp_cv <- h2o.varimp(ordinal_glm_cv)
    varimp_unrestricted <- h2o.varimp(unrestricted_ordinal_glm)

    # the values should be different
    expect_false(all(varimp_cv[,2] == varimp_unrestricted[,2]))
    expect_true(all(varimp[,2] == varimp_unrestricted[,2]))

    # the control variables should have zero importance
    expect_true(varimp_cv[varimp_cv$variable == "C2", 2] == 0)

    # in unrestricted model control variables can have larger importance than zero
    expect_true(varimp_unrestricted[varimp_unrestricted$variable == "C2", 2] > 0)
}



doTest("GLM: Control variables works with explain", glm_control_variables_ordinal)
