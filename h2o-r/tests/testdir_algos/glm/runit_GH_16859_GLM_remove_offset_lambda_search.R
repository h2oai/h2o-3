setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# GH-16859: remove_offset_effects must also work with lambda_search=TRUE.
# The offset stays part of the fit, so the model derived via
# h2o.make_unrestricted_glm_model must recover the plain offset-present model,
# while the reported remove_offset model differs from it.

prostate_frame <- function() {
    df <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    df$CAPSULE <- as.factor(df$CAPSULE)
    df$RACE <- as.factor(df$RACE)
    df$DCAPS <- as.factor(df$DCAPS)
    df$DPROS <- as.factor(df$DPROS)
    df
}

X <- c("RACE", "DCAPS", "DPROS", "PSA", "VOL", "GLEASON")
Y <- "CAPSULE"

glm_remove_offset_lambda_search_test <- function() {
    df <- prostate_frame()

    glm_offset <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df,
                          offset_column = "AGE", lambda_search = TRUE, seed = 0xC0FFEE)

    glm_ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df,
                      offset_column = "AGE", remove_offset_effects = TRUE,
                      lambda_search = TRUE, seed = 0xC0FFEE)

    glm_unrestricted <- h2o.make_unrestricted_glm_model(glm_ro)
    expect_false(is.null(glm_unrestricted))

    preds_offset <- as.data.frame(h2o.predict(glm_offset, df))
    preds_ro <- as.data.frame(h2o.predict(glm_ro, df))
    preds_unrestricted <- as.data.frame(h2o.predict(glm_unrestricted, df))

    # unrestricted model reproduces the offset-present model
    expect_equal(preds_offset$p1, preds_unrestricted$p1, tolerance = 1e-6)

    # lambda_search must select the same regularization strength (fit is identical)
    expect_equal(h2o.getLambdaBest(glm_offset), h2o.getLambdaBest(glm_ro), tolerance = 1e-12)

    # remove_offset_effects actually changes the reported predictions
    expect_gt(max(abs(preds_offset$p1 - preds_ro$p1)), 1e-6)
}

# The removed offset effect is exactly the offset: the restricted predictions must equal the plain
# offset model scored with the offset column set to zero.
glm_remove_offset_lambda_search_offset_zeroed_test <- function() {
    df <- prostate_frame()

    glm_offset <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df,
                          offset_column = "AGE", lambda_search = TRUE, seed = 0xC0FFEE)
    glm_ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df,
                      offset_column = "AGE", remove_offset_effects = TRUE,
                      lambda_search = TRUE, seed = 0xC0FFEE)

    preds_ro <- as.data.frame(h2o.predict(glm_ro, df))   # offset effect removed

    df$AGE <- 0                                          # zero the offset
    preds_zeroed <- as.data.frame(h2o.predict(glm_offset, df))

    expect_equal(preds_ro$p1, preds_zeroed$p1, tolerance = 1e-6)
}

# With remove_offset_effects + lambda_search + generate_scoring_history the model must expose both
# the restricted scoring history and the unrestricted scoring history. A plain offset model must not
# carry an unrestricted scoring history.
glm_remove_offset_lambda_search_scoring_history_test <- function() {
    df <- prostate_frame()

    glm_ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df,
                      offset_column = "AGE", remove_offset_effects = TRUE,
                      lambda_search = TRUE, generate_scoring_history = TRUE, seed = 0xC0FFEE)

    glm_offset <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df,
                          offset_column = "AGE", lambda_search = TRUE,
                          generate_scoring_history = TRUE, seed = 0xC0FFEE)

    restricted <- glm_ro@model$scoring_history
    unrestricted <- glm_ro@model$scoring_history_unrestricted_model
    plain <- glm_offset@model$scoring_history
    print(restricted)
    print(unrestricted)
    print(plain)

    expect_false(is.null(restricted))
    expect_gt(nrow(restricted), 0)
    expect_false(is.null(unrestricted))
    expect_gt(nrow(unrestricted), 0)

    # the unrestricted scoring history must match the plain offset model's scoring history
    # (drop the non-deterministic timestamp/duration columns before comparing)
    drop_cols <- function(d) d[, !(names(d) %in% c("timestamp", "duration")), drop = FALSE]
    expect_equal(drop_cols(unrestricted), drop_cols(plain), tolerance = 1e-6)

    # a plain offset model must not carry an unrestricted scoring history
    expect_true(is.null(glm_offset@model$scoring_history_unrestricted_model))
}

# The MOJO must reproduce the in-H2O (restricted) predictions of a remove_offset_effects +
# lambda_search model.
glm_remove_offset_lambda_search_mojo_test <- function() {
    df <- prostate_frame()

    glm_ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df,
                      offset_column = "AGE", remove_offset_effects = TRUE,
                      lambda_search = TRUE, seed = 0xC0FFEE)

    pred_h2o <- h2o.predict(glm_ro, df)
    mojo_path <- h2o.save_mojo(glm_ro, path = tempdir(), force = TRUE)
    mojo_model <- h2o.import_mojo(mojo_path)
    pred_mojo <- h2o.predict(mojo_model, df)

    compareFrames(pred_h2o, pred_mojo, prob = 1, tolerance = 1e-8)
}

doTest("GLM: remove_offset_effects with lambda_search",
       glm_remove_offset_lambda_search_test)
doTest("GLM: remove_offset_effects with lambda_search offset zeroed",
       glm_remove_offset_lambda_search_offset_zeroed_test)
doTest("GLM: remove_offset_effects with lambda_search scoring history",
       glm_remove_offset_lambda_search_scoring_history_test)
doTest("GLM: remove_offset_effects with lambda_search mojo",
       glm_remove_offset_lambda_search_mojo_test)
