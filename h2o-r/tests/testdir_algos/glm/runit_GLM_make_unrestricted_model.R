setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



glm_make_unrestricted_model_test <- function() {
    df <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
    df$CAPSULE <- as.factor(df$CAPSULE)
    df$RACE <- as.factor(df$RACE)
    df$DCAPS <- as.factor(df$DCAPS)
    df$DPROS <- as.factor(df$DPROS)

    response <- "CAPSULE"

    prostate_glm <- h2o.glm(family = "binomial",
                            y = response,
                            training_frame = df,
                            generate_scoring_history = T,
                            score_each_iteration = T,
                            offset_column = "AGE",
                            remove_offset_effects = T,
                            control_variables = c("PSA")
    )

    summary(prostate_glm)

    # test make unrestricted model
    unrestricted_prostate_glm <- h2o.make_unrestricted_glm_model(prostate_glm)
    expect_false(is.null(unrestricted_prostate_glm))
    summary(unrestricted_prostate_glm)

    # test make unrestricted model control variables enabled
    unrestricted_prostate_glm_cv_enabled <- h2o.make_unrestricted_glm_model(prostate_glm,
                                                                            control_variables_enabled=TRUE)
    expect_false(is.null(unrestricted_prostate_glm_cv_enabled))
    summary(unrestricted_prostate_glm_cv_enabled)

    # test make unrestricted model remove offset enabled
    unrestricted_prostate_glm_ro_enabled <- h2o.make_unrestricted_glm_model(prostate_glm,
                                                                            remove_offset_effects_enabled=TRUE)
    expect_false(is.null(unrestricted_prostate_glm_ro_enabled))
    summary(unrestricted_prostate_glm_ro_enabled)

    # should pass
    h2o.learning_curve_plot(prostate_glm)
    h2o.learning_curve_plot(unrestricted_prostate_glm)
    h2o.learning_curve_plot(unrestricted_prostate_glm_cv_enabled)
    h2o.learning_curve_plot(unrestricted_prostate_glm_ro_enabled)
    
    #should fail
    assertError(h2o.make_unrestricted_glm_model(prostate_glm, remove_offset_effects_enabled=TRUE, 
                                                control_variables_enabled=TRUE))

    prostate_glm_2 <- h2o.glm(family = "binomial",
                            y = response,
                            training_frame = df,
                            generate_scoring_history = T,
                            score_each_iteration = T,
                            offset_column = "AGE",
                            control_variables = c("PSA"))

    assertError(h2o.make_unrestricted_glm_model(prostate_glm_2, remove_offset_effects_enabled=TRUE))
    assertError(h2o.make_unrestricted_glm_model(prostate_glm_2, control_variables_enabled=TRUE))
}

doTest("GLM: Test make unrestricted model", glm_make_unrestricted_model_test)
