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

    # should pass
    h2o.learning_curve_plot(prostate_glm)
    h2o.learning_curve_plot(unrestricted_prostate_glm)
}

doTest("GLM: Test make unrestricted model", glm_make_unrestricted_model_test)
