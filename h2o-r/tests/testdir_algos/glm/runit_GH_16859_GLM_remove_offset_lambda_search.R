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

    # the main scoring_history must be the RESTRICTED (offset-removed) table, not the unrestricted one:
    # removing the offset changes the deviance, so their deviance_train columns must differ. If the
    # restricted/unrestricted tables were swapped, every value would match and this would fail.
    n <- min(nrow(restricted), nrow(unrestricted))
    expect_true(any(abs(restricted$deviance_train[1:n] - unrestricted$deviance_train[1:n]) > 1e-6))

    # a plain offset model must not carry an unrestricted scoring history
    expect_true(is.null(glm_offset@model$scoring_history_unrestricted_model))
}

# remove_offset_effects + lambda_search + cross-validation: the model trains and the restricted scoring
# history's cross-validation deviance is offset-removed, so its deviance_xval must differ from the
# unrestricted history's deviance_xval (removing the offset changes the deviance).
glm_remove_offset_lambda_search_cross_validation_test <- function() {
    df <- prostate_frame()
    glm_ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                      lambda_search = TRUE, remove_offset_effects = TRUE, nfolds = 3,
                      generate_scoring_history = TRUE, seed = 0xC0FFEE)

    # same settings, offset kept -> known-correct oracle for the unrestricted xval deviance (fit unchanged)
    glm_plain <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                         lambda_search = TRUE, nfolds = 3, generate_scoring_history = TRUE, seed = 0xC0FFEE)

    restricted <- glm_ro@model$scoring_history
    unrestricted <- glm_ro@model$scoring_history_unrestricted_model
    plain <- glm_plain@model$scoring_history
    expect_false(is.null(restricted))
    expect_false(is.null(unrestricted))
    expect_true("deviance_xval" %in% names(restricted))
    expect_true("deviance_xval" %in% names(unrestricted))
    expect_true("deviance_xval" %in% names(plain))

    n <- min(nrow(restricted), nrow(unrestricted), nrow(plain))
    r <- suppressWarnings(as.numeric(restricted$deviance_xval[1:n]))
    u <- suppressWarnings(as.numeric(unrestricted$deviance_xval[1:n]))
    p <- suppressWarnings(as.numeric(plain$deviance_xval[1:n]))
    ok <- !is.na(r) & !is.na(u) & !is.na(p)  # early-stop-only rows carry empty cells
    expect_true(any(ok))
    # the unrestricted xval deviance must equal the plain offset model's (the fit is unchanged)
    expect_equal(u[ok], p[ok], tolerance = 1e-8)
    # a correct offset-removed deviance is a real number >= 0; the not-populated sentinel is NaN, excluded by `ok`
    expect_true(any(r[ok] >= 0 & abs(r[ok] - u[ok]) > 1e-8))
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

# The restricted deviance recompute (GLMResDevTask) is family-specific, so it must be exercised beyond
# binomial - including the non-canonical families (gamma, tweedie, negativebinomial). For each family the
# unrestricted model must recover the plain offset model and the restricted predictions must differ.
glm_remove_offset_lambda_search_families_test <- function() {
    df <- prostate_frame()
    cases <- list(
        list(family = "gaussian", y = "VOL", x = c("RACE", "DPROS", "PSA", "GLEASON"), extra = list()),
        list(family = "poisson", y = "GLEASON", x = c("RACE", "DPROS", "PSA", "VOL"), extra = list()),
        list(family = "gamma", y = "PSA", x = c("RACE", "DPROS", "VOL", "GLEASON"), extra = list()),  # PSA > 0
        list(family = "negativebinomial", y = "GLEASON", x = c("RACE", "DPROS", "PSA", "VOL"),
             extra = list(theta = 0.5)),
        list(family = "tweedie", y = "VOL", x = c("RACE", "DPROS", "PSA", "GLEASON"),
             extra = list(tweedie_variance_power = 1.5, tweedie_link_power = 0.0))
    )
    for (cfg in cases) {
        base <- do.call(h2o.glm, c(list(family = cfg$family, x = cfg$x, y = cfg$y, training_frame = df,
                                        offset_column = "AGE", lambda_search = TRUE, seed = 0xC0FFEE), cfg$extra))
        ro <- do.call(h2o.glm, c(list(family = cfg$family, x = cfg$x, y = cfg$y, training_frame = df,
                                      offset_column = "AGE", remove_offset_effects = TRUE,
                                      lambda_search = TRUE, seed = 0xC0FFEE), cfg$extra))

        unrestricted <- h2o.make_unrestricted_glm_model(ro)
        expect_equal(h2o.coef(base), h2o.coef(unrestricted), tolerance = 1e-6)

        pb <- as.data.frame(h2o.predict(base, df))$predict
        pr <- as.data.frame(h2o.predict(ro, df))$predict
        expect_gt(max(abs(pb - pr)), 1e-6)
    }
}

# A weights column feeds the restricted deviance sums; the fit stays identical.
glm_remove_offset_lambda_search_weights_test <- function() {
    df <- prostate_frame()
    df$w <- df$VOL + 1  # positive, non-constant weights

    base <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                    weights_column = "w", lambda_search = TRUE, seed = 0xC0FFEE)
    ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                  weights_column = "w", remove_offset_effects = TRUE, lambda_search = TRUE, seed = 0xC0FFEE)

    unrestricted <- h2o.make_unrestricted_glm_model(ro)
    expect_equal(h2o.coef(base), h2o.coef(unrestricted), tolerance = 1e-6)
    preds_offset <- as.data.frame(h2o.predict(base, df))$p1
    preds_ro <- as.data.frame(h2o.predict(ro, df))$p1
    expect_gt(max(abs(preds_offset - preds_ro)), 1e-6)
}

# A genuine holdout (not the training frame) exercises the restricted validation-deviance path. The
# unrestricted model's validation metrics must match the plain offset model on that holdout.
glm_remove_offset_lambda_search_validation_test <- function() {
    df <- prostate_frame()
    splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1234)
    train <- splits[[1]]
    valid <- splits[[2]]

    base <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = train, validation_frame = valid,
                    offset_column = "AGE", lambda_search = TRUE, seed = 0xC0FFEE)
    ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = train, validation_frame = valid,
                  offset_column = "AGE", remove_offset_effects = TRUE, lambda_search = TRUE, seed = 0xC0FFEE)

    unrestricted <- h2o.make_unrestricted_glm_model(ro)
    perf_base <- h2o.performance(base, valid)
    perf_unr <- h2o.performance(unrestricted, valid)
    expect_equal(h2o.rmse(perf_base), h2o.rmse(perf_unr), tolerance = 1e-6)
    expect_equal(h2o.mse(perf_base), h2o.mse(perf_unr), tolerance = 1e-6)
}

# beta_constraints route through a separate scoring path; the combination must still recover the plain
# (constrained) offset model.
glm_remove_offset_lambda_search_beta_constraints_test <- function() {
    df <- prostate_frame()
    bc <- as.h2o(data.frame(names = c("PSA", "VOL"), lower_bounds = c(-1, -1), upper_bounds = c(1, 1)))

    base <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                    beta_constraints = bc, lambda_search = TRUE, seed = 0xC0FFEE)
    ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                  beta_constraints = bc, remove_offset_effects = TRUE, lambda_search = TRUE, seed = 0xC0FFEE)

    unrestricted <- h2o.make_unrestricted_glm_model(ro)
    expect_equal(h2o.coef(base), h2o.coef(unrestricted), tolerance = 1e-6)
    preds_offset <- as.data.frame(h2o.predict(base, df))$p1
    preds_ro <- as.data.frame(h2o.predict(ro, df))$p1
    expect_gt(max(abs(preds_offset - preds_ro)), 1e-6)
}

# early_stopping can break the lambda loop mid-search; the unrestricted model must still recover the plain
# offset model at the same selected lambda.
glm_remove_offset_lambda_search_early_stopping_test <- function() {
    df <- prostate_frame()

    base <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                    lambda_search = TRUE, early_stopping = TRUE, seed = 0xC0FFEE)
    ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                  lambda_search = TRUE, early_stopping = TRUE, remove_offset_effects = TRUE, seed = 0xC0FFEE)

    expect_equal(h2o.getLambdaBest(base), h2o.getLambdaBest(ro), tolerance = 1e-12)
    unrestricted <- h2o.make_unrestricted_glm_model(ro)
    expect_equal(h2o.coef(base), h2o.coef(unrestricted), tolerance = 1e-6)
}

# The R client rejects the invalid derive combinations before they reach the server, so the error paths need
# their own coverage - a client-side regression here would otherwise surface only as a confusing server error.
glm_remove_offset_lambda_search_derive_errors_test <- function() {
    df <- prostate_frame()

    plain <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                     lambda_search = TRUE, seed = 0xC0FFEE)
    ro <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                  lambda_search = TRUE, remove_offset_effects = TRUE, seed = 0xC0FFEE)

    # a model trained without either feature has no restricted/unrestricted split to expose
    expect_error(h2o.make_unrestricted_glm_model(plain), "control_variables or remove_offset_effects")
    expect_error(h2o.make_derived_glm_model(ro, remove_control_variables_effects = TRUE),
                 "requires the source model to have been trained with control_variables")
    expect_error(h2o.make_derived_glm_model(plain, remove_offset_effects = TRUE),
                 "control_variables or remove_offset_effects")
    # both set would just reproduce the main model. Needs a source trained with both features, otherwise the
    # missing-control_variables check above fires first and this branch is never reached. control_variables is
    # not supported with lambda_search, so this one model is fit without it.
    both <- h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                    control_variables = c("PSA"), remove_offset_effects = TRUE, seed = 0xC0FFEE)
    expect_error(h2o.make_derived_glm_model(both, remove_control_variables_effects = TRUE, remove_offset_effects = TRUE),
                 "cannot both be TRUE")

    # a reporting-only derived model holds no solver state, so it cannot seed a continuation
    derived <- h2o.make_unrestricted_glm_model(ro)
    expect_error(h2o.glm(family = "binomial", x = X, y = Y, training_frame = df, offset_column = "AGE",
                         remove_offset_effects = TRUE, solver = "IRLSM", checkpoint = derived@model_id),
                 "reporting view")
}

# NOTE: the sparse-standardized-data case lives only in Java
# (GLMRemoveOffsetLambdaSearchTest.sparseDataWorksWithLambdaSearch). Forcing the sparse chunk path needs a
# genuinely sparse-encoded frame (Java TestFrameBuilder); an as.h2o data.frame does not produce one.

# doTest quits the R process (PASS() -> q()) after the first call, so multiple sequential doTest calls
# would silently skip every test but the first. Run all tests through a single doSuite instead.
doSuite("GLM: remove_offset_effects with lambda_search", makeSuite(
    glm_remove_offset_lambda_search_test,
    glm_remove_offset_lambda_search_offset_zeroed_test,
    glm_remove_offset_lambda_search_scoring_history_test,
    glm_remove_offset_lambda_search_cross_validation_test,
    glm_remove_offset_lambda_search_mojo_test,
    glm_remove_offset_lambda_search_families_test,
    glm_remove_offset_lambda_search_weights_test,
    glm_remove_offset_lambda_search_validation_test,
    glm_remove_offset_lambda_search_beta_constraints_test,
    glm_remove_offset_lambda_search_early_stopping_test,
    glm_remove_offset_lambda_search_derive_errors_test
))
