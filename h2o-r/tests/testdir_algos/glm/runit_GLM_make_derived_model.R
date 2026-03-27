setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

glm_make_derived_model_test <- function() {
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

    # test make derived model
    derived_prostate_glm <- h2o.make_derived_glm_model(prostate_glm)
    expect_false(is.null(derived_prostate_glm))
    summary(derived_prostate_glm)

    # test make derived model with remove control variables effects
    derived_prostate_glm_cv_enabled <- h2o.make_derived_glm_model(prostate_glm,
                                                                  remove_control_variables_effects=TRUE)
    expect_false(is.null(derived_prostate_glm_cv_enabled))
    summary(derived_prostate_glm_cv_enabled)

    # test make derived model with remove offset effects
    derived_prostate_glm_ro_enabled <- h2o.make_derived_glm_model(prostate_glm,
                                                                  remove_offset_effects=TRUE)
    expect_false(is.null(derived_prostate_glm_ro_enabled))
    summary(derived_prostate_glm_ro_enabled)

    # should pass
    h2o.learning_curve_plot(prostate_glm)
    h2o.learning_curve_plot(derived_prostate_glm)
    h2o.learning_curve_plot(derived_prostate_glm_cv_enabled)
    h2o.learning_curve_plot(derived_prostate_glm_ro_enabled)

    #should fail
    assertError(h2o.make_derived_glm_model(prostate_glm, remove_offset_effects=TRUE,
                                           remove_control_variables=TRUE))

    prostate_glm_2 <- h2o.glm(family = "binomial",
                              y = response,
                              training_frame = df,
                              generate_scoring_history = T,
                              score_each_iteration = T,
                              offset_column = "AGE",
                              control_variables = c("PSA"))

    assertError(h2o.make_derived_glm_model(prostate_glm_2, remove_offset_effects=TRUE))
    assertError(h2o.make_derived_glm_model(prostate_glm_2, remove_control_variables_effects=TRUE))
}

doTest("GLM: Test make derived model", glm_make_derived_model_test)
