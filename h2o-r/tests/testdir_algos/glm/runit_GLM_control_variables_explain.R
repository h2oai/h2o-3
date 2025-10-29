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

    # test make unrestricted model
    unrestricted_prostate_glm <- h2o.make_unrestricted_glm_model(prostate_glm)
    expect_false(is.null(unrestricted_prostate_glm))
    summary(unrestricted_prostate_glm)

    # should pass
    h2o.learning_curve_plot(prostate_glm)
    h2o.learning_curve_plot(unrestricted_prostate_glm)
    
    # should pass 
    h2o.explain(prostate_glm, df)
    h2o.explain(unrestricted_prostate_glm, df)
    
    # test variable importance
    varimp <- h2o.varimp(prostate_glm)
    varimp_unrestricted <- h2o.varimp(unrestricted_prostate_glm)
    
    # the values should be different
    expect_false(all(varimp[,1] == varimp_unrestricted[,1]))
    
    # the control variables should have zero importance
    expect_true(varimp[varimp$variable == "RACE.0", 2] == 0)
    expect_true(varimp[varimp$variable == "RACE.1", 2] == 0)
    expect_true(varimp[varimp$variable == "RACE.2", 2] == 0)

    # in unrestricted model control variables can have larger importance than zero
    expect_true(varimp_unrestricted[varimp_unrestricted$variable == "RACE.0", 2] >= 0)
    expect_true(varimp_unrestricted[varimp_unrestricted$variable == "RACE.1", 2] >= 0)
    expect_true(varimp_unrestricted[varimp_unrestricted$variable == "RACE.2", 2] >= 0)
}

doTest("GLM: Control variables works with expain", glm_control_variables_explain)
