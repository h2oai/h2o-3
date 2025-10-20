setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



glm_control_variables_explain <- function() {
    df <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
    df$CAPSULE <- as.factor(df$CAPSULE)
    df$RACE <- as.factor(df$RACE)
    df$DCAPS <- as.factor(df$DCAPS)
    df$DPROS <- as.factor(df$DPROS)

    response <- "CAPSULE"

    prostate_glm <- h2o.glm(family = "binomial",
                            y = response,
                            training_frame = df,
                            control_variables = "RACE",
                            generate_scoring_history = T,
                            score_each_iteration = T,
    )

    summary(prostate_glm)

    unrestricted_prostate_glm <- h2o.makeUnrestrictedGLMModel(prostate_glm)

    summary(unrestricted_prostate_glm)

    h2o.learning_curve_plot(prostate_glm)

    h2o.learning_curve_plot(unrestricted_prostate_glm)
    
    h2o.explain(unrestricted_prostate_glm, df)
    h2o.explain(prostate_glm, df)
}

doTest("GLM: Control variables works with expain", glm_control_variables_explain)
