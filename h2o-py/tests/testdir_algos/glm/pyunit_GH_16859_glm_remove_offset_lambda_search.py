import sys
sys.path.insert(1, "../../../")
import tempfile
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


# GH-16859: remove_offset_effects must also work with lambda_search=True.
# remove_offset_effects does not change the fit (the offset is still part of the
# optimization), it only strips the offset contribution from the reported model.


# Two assertions in this suite compare models that were trained independently (the plain offset model against
# the remove_offset model, whose fit is supposed to be identical). Those go through IRLSM and an MRTask reduce
# whose summation order is not pinned run to run, so they are compared to a tolerance rather than bit-exactly.
COEF_DELTA = 1e-6
PRED_DELTA = 1e-6
LAMBDA_DELTA = 1e-10


def _cars_with_offset():
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    cars["cylinders"] = cars["cylinders"].asfactor()   # keeps a categorical predictor in the design matrix
    # A row-varying offset. A constant one is absorbed by the intercept, which leaves the restricted and
    # unrestricted views differing by a single global shift - enough to satisfy the "predictions differ"
    # assertions while hiding any per-row error in the offset handling. `economy` is excluded from the
    # predictors and the offset alike: economy_20mpg is derived from it.
    cars["offset"] = cars["acceleration"] / 10.0
    return cars, ["cylinders", "displacement", "power", "weight", "year"], "economy_20mpg", "offset"


# The model derived via make_unrestricted_glm_model must recover the plain offset-present model
# (same coefficients, predictions and selected lambda), while the reported predictions differ.
def glm_remove_offset_lambda_search():
    cars, x, y, offset_col = _cars_with_offset()

    glm_offset = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, seed=0xC0FFEE)
    glm_offset.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    glm_ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                           remove_offset_effects=True, seed=0xC0FFEE)
    glm_ro.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    glm_unrestricted = glm_ro.make_unrestricted_glm_model()
    assert glm_unrestricted is not None, "make_unrestricted_glm_model returned None"

    preds_offset = glm_offset.predict(cars).as_data_frame()
    preds_ro = glm_ro.predict(cars).as_data_frame()
    preds_unrestricted = glm_unrestricted.predict(cars).as_data_frame()

    # unrestricted model must reproduce the offset-present model (same fitted beta)
    for k in glm_offset.coef().keys():
        pyunit_utils.assert_equals(glm_offset.coef()[k], glm_unrestricted.coef().get(k, float("nan")),
                                   f"Coefficient {k} differs between offset model and unrestricted model!", delta=COEF_DELTA)

    # lambda_search must select the same regularization strength (fit is identical)
    pyunit_utils.assert_equals(H2OGeneralizedLinearEstimator.getLambdaBest(glm_offset),
                               H2OGeneralizedLinearEstimator.getLambdaBest(glm_ro),
                               "Selected lambda_best differs between offset model and remove_offset model!", delta=LAMBDA_DELTA)

    for i in range(preds_offset.shape[0]):
        pyunit_utils.assert_equals(preds_offset.iloc[i, 1], preds_unrestricted.iloc[i, 1],
                                   f"Prediction {i} should match offset-present model but doesn't!", delta=PRED_DELTA)

    # remove_offset_effects must actually change the reported predictions
    assert (preds_offset.iloc[:, 1] - preds_ro.iloc[:, 1]).abs().max() > 1e-6, \
        "Predictions should differ once the offset effect is removed!"


# The removed offset effect is exactly the offset: the restricted predictions must equal the plain
# offset model scored with the offset column set to zero.
def glm_remove_offset_lambda_search_offset_zeroed():
    cars, x, y, offset_col = _cars_with_offset()

    glm_offset = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, seed=0xC0FFEE)
    glm_offset.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    glm_ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                           remove_offset_effects=True, seed=0xC0FFEE)
    glm_ro.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    preds_ro = glm_ro.predict(cars).as_data_frame()   # offset effect removed

    cars[offset_col] = 0                              # zero out the offset
    preds_zeroed = glm_offset.predict(cars).as_data_frame()

    for i in range(preds_ro.shape[0]):
        pyunit_utils.assert_equals(preds_ro.iloc[i, 1], preds_zeroed.iloc[i, 1],
                                   f"Prediction {i}: restricted model must equal offset-zeroed model!", delta=PRED_DELTA)


# With remove_offset_effects + lambda_search + generate_scoring_history the model must expose both the
# restricted scoring history and the unrestricted scoring history, and the unrestricted one must
# reproduce the plain offset model's scoring history. A plain offset model has no unrestricted history.
def glm_remove_offset_lambda_search_scoring_history():
    cars, x, y, offset_col = _cars_with_offset()

    glm_ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                           remove_offset_effects=True, generate_scoring_history=True,
                                           seed=0xC0FFEE)
    glm_ro.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    glm_offset = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                               generate_scoring_history=True, seed=0xC0FFEE)
    glm_offset.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    restricted = glm_ro._model_json["output"]["scoring_history"]
    unrestricted = glm_ro._model_json["output"]["scoring_history_unrestricted_model"]
    plain = glm_offset._model_json["output"]["scoring_history"]
    print("Restricted scoring history:\n", restricted)
    print("Unrestricted scoring history:\n", unrestricted)
    print("Plain offset model scoring history:\n", plain)

    assert restricted is not None and len(restricted.cell_values) > 0, \
        "Restricted scoring history should be present and non-empty"
    assert unrestricted is not None and len(unrestricted.cell_values) > 0, \
        "Unrestricted scoring history should be present and non-empty when remove_offset_effects is on"

    # Both histories get a row per scoring event, so they must be the same length. Asserted explicitly because the
    # deviance comparison below uses zip(), which would silently tolerate a restricted table that is short by its
    # last (selected-lambda) row - exactly the off-by-one this publication ordering is fragile to.
    assert len(restricted.cell_values) == len(unrestricted.cell_values), \
        "The restricted lambda history must have a row for every scoring event, including the last: got %d vs %d" \
        % (len(restricted.cell_values), len(unrestricted.cell_values))

    # the unrestricted scoring history must match the plain offset model's scoring history
    # (compare every column except the non-deterministic timestamp/duration)
    cols_to_compare = [c for c in unrestricted.col_header if c not in ("timestamp", "duration")]
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(unrestricted, plain, cols_to_compare)

    # the main scoring_history must be the RESTRICTED (offset-removed) table, not the unrestricted one:
    # removing the offset changes the deviance, so their deviance_train columns must differ. If the
    # restricted/unrestricted tables were swapped, every value would match and this would fail.
    dev_col = restricted.col_header.index("deviance_train")
    restricted_dev = [row[dev_col] for row in restricted.cell_values]
    unrestricted_dev = [row[dev_col] for row in unrestricted.cell_values]
    assert any(abs(r - u) > 1e-6 for r, u in zip(restricted_dev, unrestricted_dev)), \
        "Main scoring_history must be the restricted (offset-removed) table: its deviance_train must " \
        "differ from the unrestricted history's"

    assert glm_offset._model_json["output"]["scoring_history_unrestricted_model"] is None, \
        "Plain offset model should not have an unrestricted scoring history"


# generate_scoring_history adds a scoring event per iteration, and the unrestricted lambda history gets a row
# for each. The restricted history must get one too, otherwise combineScoringHistory pads the missing rows and
# the main scoring_history (the one the user sees) ends up with null lambda/deviance cells - and the checkpoint
# restore, which parses those cells, blows up. Both are asserted here.
def glm_remove_offset_lambda_search_scoring_history_no_gaps():
    df = _prostate_with_offset()
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    x = ["RACE", "DPROS", "PSA", "VOL", "GLEASON"]
    common = dict(family="binomial", lambda_search=True, remove_offset_effects=True,
                  generate_scoring_history=True, score_each_iteration=True, solver="IRLSM", seed=0xC0FFEE)

    glm_ro = H2OGeneralizedLinearEstimator(nlambdas=8, **common)
    glm_ro.train(x=x, y="CAPSULE", training_frame=df, offset_column="off", validation_frame=df)

    lambda_cols = ["lambda", "predictors", "deviance_train", "deviance_test", "alpha"]
    for slot in ("scoring_history", "scoring_history_unrestricted_model"):
        table = glm_ro._model_json["output"][slot]
        assert table is not None, "%s must be present" % slot
        present = [c for c in lambda_cols if c in table.col_header]
        assert present, "%s must expose the per-lambda columns" % slot
        idx = {c: table.col_header.index(c) for c in present}
        for rown, row in enumerate(table.cell_values):
            empty = [c for c in present if row[idx[c]] is None or row[idx[c]] == ""]
            assert not empty, \
                "%s row %d has empty per-lambda cells %s - the restricted lambda history is missing the " \
                "per-iteration rows that generate_scoring_history produces" % (slot, rown, empty)

    # continuing from the checkpoint parses the lambda cells of the stored history, so a gap crashes here
    continued = H2OGeneralizedLinearEstimator(nlambdas=16, checkpoint=glm_ro.model_id, **common)
    continued.train(x=x, y="CAPSULE", training_frame=df, offset_column="off", validation_frame=df)
    assert continued._model_json["output"]["scoring_history"] is not None, \
        "checkpoint continuation must produce a scoring history"


# lambda_search is deliberately NOT pinned across a checkpoint continuation (only remove_offset_effects is), so
# "fit without regularization search, then refine with lambda_search" keeps working. The two modes store their
# scoring history in different formats, so the old history is dropped rather than mis-parsed.
def glm_remove_offset_lambda_search_checkpoint_may_enable_lambda_search():
    df = _prostate_with_offset()
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    x = ["RACE", "DPROS", "PSA", "VOL", "GLEASON"]
    common = dict(family="binomial", remove_offset_effects=True, solver="IRLSM", seed=0xC0FFEE)

    base = H2OGeneralizedLinearEstimator(lambda_search=False, max_iterations=5, **common)
    base.train(x=x, y="CAPSULE", training_frame=df, offset_column="off")

    continued = H2OGeneralizedLinearEstimator(lambda_search=True, nlambdas=6,
                                              checkpoint=base.model_id, **common)
    continued.train(x=x, y="CAPSULE", training_frame=df, offset_column="off")

    history = continued._model_json["output"]["scoring_history"]
    assert history is not None and len(history.cell_values) > 0, \
        "enabling lambda_search on a continuation must still produce a scoring history"
    assert "lambda" in history.col_header, \
        "the continued model's scoring history must be in lambda format"
    # remove_offset_effects stays pinned, so flipping it must still be refused
    try:
        bad = H2OGeneralizedLinearEstimator(lambda_search=True, checkpoint=base.model_id,
                                            family="binomial", remove_offset_effects=False,
                                            solver="IRLSM", seed=0xC0FFEE)
        bad.train(x=x, y="CAPSULE", training_frame=df, offset_column="off")
        assert False, "flipping remove_offset_effects across a checkpoint must be rejected"
    except Exception as e:
        assert "_remove_offset_effects" in str(e), \
            "expected a _remove_offset_effects checkpoint error, got: %s" % str(e)[:300]


# remove_offset_effects + lambda_search + cross-validation: the model trains and the restricted scoring
# history's cross-validation deviance is offset-removed, so its deviance_xval must differ from the
# unrestricted history's deviance_xval (removing the offset changes the deviance).
def glm_remove_offset_lambda_search_cross_validation():
    cars, x, y, offset_col = _cars_with_offset()

    glm_ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, remove_offset_effects=True,
                                           nfolds=3, generate_scoring_history=True, seed=0xC0FFEE)
    glm_ro.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    # same settings, offset kept -> known-correct oracle for the unrestricted xval deviance (fit unchanged)
    glm_plain = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, nfolds=3,
                                              generate_scoring_history=True, seed=0xC0FFEE)
    glm_plain.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    restricted = glm_ro._model_json["output"]["scoring_history"]
    unrestricted = glm_ro._model_json["output"]["scoring_history_unrestricted_model"]
    plain = glm_plain._model_json["output"]["scoring_history"]
    assert restricted is not None and unrestricted is not None and plain is not None
    for t in (restricted, unrestricted, plain):
        assert "deviance_xval" in t.col_header, "cross-validation must add a deviance_xval column"

    xv = restricted.col_header.index("deviance_xval")
    triples = [(rr[xv], ur[xv], pr[xv]) for rr, ur, pr in zip(restricted.cell_values, unrestricted.cell_values,
                                                              plain.cell_values)]
    # keep only rows where all three are numeric (early-stop-only rows carry empty cells)
    triples = [(r, u, p) for r, u, p in triples
               if isinstance(r, (int, float)) and isinstance(u, (int, float)) and isinstance(p, (int, float))]
    assert triples, "restricted history must expose a numeric deviance_xval under cross-validation"
    # the unrestricted xval deviance must equal the plain offset model's (the fit is unchanged)
    for r, u, p in triples:
        assert abs(u - p) < 1e-8, "unrestricted deviance_xval must match the plain offset model: %r vs %r" % (u, p)
    # a correct offset-removed deviance is a real number >= 0; NaN is the not-populated sentinel and must not count
    assert any(r == r and r >= 0 and abs(r - u) > 1e-8 for r, u, p in triples), \
        "restricted deviance_xval must be a real offset-removed value differing from the unrestricted history's"

    # The assertion above pins only "differs from unrestricted"; it would still pass on a value off by a
    # factor of nobs, summed instead of averaged, or negated. Anchor the column's *scale* to the model's own
    # restricted CV deviance. Not an exact identity - the scoring-history value is an unweighted mean over
    # folds while the CV metric pools holdout rows, so they coincide only for equal fold sizes; measured
    # agreement is ~0.01%, so 5% is loose enough to be stable and tight enough that any scale or sign error
    # (which are order-of-magnitude effects) fails. Note this band does NOT by itself separate the restricted
    # column from the unrestricted one - they sit ~0.25% apart - which is what the assertion above is for.
    xval_perf = glm_ro.model_performance(xval=True)
    expected = xval_perf.residual_deviance() / xval_perf.nobs()
    restricted_xval = [r for r, u, p in triples if r == r]
    closest = min(restricted_xval, key=lambda r: abs(r - expected))
    assert abs(closest - expected) <= 0.05 * abs(expected), \
        "restricted deviance_xval (%r) must be on the scale of the model's own offset-removed CV deviance " \
        "(%r); a factor-of-nobs, summed-not-averaged or sign error would show up here" % (closest, expected)

    # documented workflow: make_unrestricted_glm_model on a CV model propagates the unrestricted CV metrics
    # into the derived model's cross_validation_metrics, matching the plain offset model (this exercises the
    # cross_validation_metrics* model-object path, distinct from the scoring_history deviance_xval above).
    assert glm_ro._model_json["output"]["cross_validation_metrics_unrestricted_model"] is not None, \
        "remove_offset CV model must expose cross_validation_metrics_unrestricted_model"
    unrestricted = glm_ro.make_unrestricted_glm_model()
    pyunit_utils.assert_equals(glm_plain.model_performance(xval=True).residual_deviance(),
                               unrestricted.model_performance(xval=True).residual_deviance(),
                               "unrestricted CV residual deviance must match the plain offset model", delta=1e-4)


# The MOJO must reproduce the in-H2O (restricted) predictions of a remove_offset_effects +
# lambda_search model.
def glm_remove_offset_lambda_search_mojo():
    cars, x, y, offset_col = _cars_with_offset()

    glm_ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                           remove_offset_effects=True, seed=0xC0FFEE)
    glm_ro.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    pred_h2o = glm_ro.predict(cars)
    mojo_path = glm_ro.save_mojo(path=tempfile.mkdtemp())
    mojo_model = h2o.import_mojo(mojo_path)
    pred_mojo = mojo_model.predict(cars)

    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, prob=1, tol=1e-8)


def _prostate_with_offset():
    df = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    df["off"] = df["AGE"] / 100.0
    return df


# The restricted deviance recompute (GLMResDevTask) is family-specific, so it must be exercised beyond
# binomial - including the non-canonical families (gamma, tweedie, negativebinomial) where the per-row
# deviance/likelihood relationship differs. For each family the unrestricted model must recover the plain
# offset model and the restricted predictions must differ.
def glm_remove_offset_lambda_search_families():
    df = _prostate_with_offset()
    df["pos"] = df["PSA"] + 1.0  # strictly positive response for gamma
    # (family, response, predictors, extra kwargs)
    cases = [
        ("gaussian", "VOL", ["RACE", "DPROS", "PSA", "GLEASON"], {}),
        ("poisson", "GLEASON", ["RACE", "DPROS", "PSA", "VOL"], {}),
        ("gamma", "pos", ["RACE", "DPROS", "VOL", "GLEASON"], {}),
        ("negativebinomial", "GLEASON", ["RACE", "DPROS", "PSA", "VOL"], {"theta": 0.5}),
        ("tweedie", "VOL", ["RACE", "DPROS", "PSA", "GLEASON"],
         {"tweedie_variance_power": 1.5, "tweedie_link_power": 0.0}),
    ]
    for family, y, x, kw in cases:
        base = H2OGeneralizedLinearEstimator(family=family, lambda_search=True, seed=0xC0FFEE, **kw)
        base.train(x=x, y=y, training_frame=df, offset_column="off")
        ro = H2OGeneralizedLinearEstimator(family=family, lambda_search=True,
                                           remove_offset_effects=True, seed=0xC0FFEE, **kw)
        ro.train(x=x, y=y, training_frame=df, offset_column="off")

        unrestricted = ro.make_unrestricted_glm_model()
        for k in base.coef().keys():
            pyunit_utils.assert_equals(base.coef()[k], unrestricted.coef().get(k, float("nan")),
                                       f"[{family}] coef {k}: unrestricted model must recover plain offset model", delta=COEF_DELTA)

        pb = base.predict(df).as_data_frame()["predict"]
        pr = ro.predict(df).as_data_frame()["predict"]
        assert (pb - pr).abs().max() > 1e-6, f"[{family}] restricted predictions should differ from plain offset model"

        # The assertions above cannot fail on a broken deviance: the fit is untouched, so the coefficients
        # always match and the predictions always differ. GLMResDevTask.alsoComputeOffsetRemoved() is
        # family-specific, so it needs a real oracle per family - the restricted residual deviance must equal
        # the deviance of the *same* fit scored with the offset column zeroed out.
        df_zero = df.cbind(h2o.H2OFrame([[0.0]] * df.nrows, column_names=["off_zero"]))
        df_zero["off"] = df_zero["off_zero"]
        dev_restricted = ro.model_performance(df).residual_deviance()
        dev_offset_zeroed = base.model_performance(df_zero).residual_deviance()
        pyunit_utils.assert_equals(
            dev_offset_zeroed, dev_restricted,
            f"[{family}] restricted residual deviance must equal the offset-zeroed deviance of the same fit",
            delta=max(1e-6, abs(dev_offset_zeroed) * 1e-8))


# A weights column feeds into the restricted deviance sums; the fit stays identical, so the unrestricted
# model must still recover the plain (weighted) offset model and the restricted predictions must differ.
def glm_remove_offset_lambda_search_weights():
    df = _prostate_with_offset()
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    df["w"] = df["ID"] % 3 + 1  # positive integer weights
    x, y = ["RACE", "DPROS", "PSA", "VOL", "GLEASON"], "CAPSULE"

    base = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, seed=0xC0FFEE)
    base.train(x=x, y=y, training_frame=df, offset_column="off", weights_column="w")
    ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                       remove_offset_effects=True, seed=0xC0FFEE)
    ro.train(x=x, y=y, training_frame=df, offset_column="off", weights_column="w")

    unrestricted = ro.make_unrestricted_glm_model()
    for k in base.coef().keys():
        pyunit_utils.assert_equals(base.coef()[k], unrestricted.coef().get(k, float("nan")),
                                   f"[weights] coef {k}: unrestricted model must recover plain offset model", delta=COEF_DELTA)
    pb = base.predict(df).as_data_frame()["p1"]
    pr = ro.predict(df).as_data_frame()["p1"]
    assert (pb - pr).abs().max() > 1e-6, "[weights] restricted predictions should differ from plain offset model"


# All existing tests reuse the training frame as validation; this uses a genuine holdout so the restricted
# validation-deviance path (GLMResDevTask on a distinct _validDinfo) is exercised. The unrestricted model's
# validation metrics must match the plain offset model on the same holdout.
def glm_remove_offset_lambda_search_validation():
    df = _prostate_with_offset()
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    train, valid = df.split_frame(ratios=[0.8], seed=1234)
    x, y = ["RACE", "DPROS", "PSA", "VOL", "GLEASON"], "CAPSULE"

    base = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, seed=0xC0FFEE)
    base.train(x=x, y=y, training_frame=train, validation_frame=valid, offset_column="off")
    ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                       remove_offset_effects=True, seed=0xC0FFEE)
    ro.train(x=x, y=y, training_frame=train, validation_frame=valid, offset_column="off")

    unrestricted = ro.make_unrestricted_glm_model()
    perf_base = base.model_performance(valid)
    perf_unr = unrestricted.model_performance(valid)
    pyunit_utils.assert_equals(perf_base.rmse(), perf_unr.rmse(),
                               "validation rmse: unrestricted model must match plain offset model", delta=1e-6)
    pyunit_utils.assert_equals(perf_base.mse(), perf_unr.mse(),
                               "validation mse: unrestricted model must match plain offset model", delta=1e-6)


# beta_constraints route through a separate scoring path; the combination with lambda_search +
# remove_offset_effects must still recover the plain (constrained) offset model.
def glm_remove_offset_lambda_search_beta_constraints():
    df = _prostate_with_offset()
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    x, y = ["RACE", "DPROS", "PSA", "VOL", "GLEASON"], "CAPSULE"
    bc = h2o.H2OFrame({"names": ["PSA", "VOL"], "lower_bounds": [-1.0, -1.0], "upper_bounds": [1.0, 1.0]})
    bc = bc[["names", "lower_bounds", "upper_bounds"]]

    base = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                         beta_constraints=bc, seed=0xC0FFEE)
    base.train(x=x, y=y, training_frame=df, offset_column="off")
    ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, remove_offset_effects=True,
                                       beta_constraints=bc, seed=0xC0FFEE)
    ro.train(x=x, y=y, training_frame=df, offset_column="off")

    unrestricted = ro.make_unrestricted_glm_model()
    for k in base.coef().keys():
        pyunit_utils.assert_equals(base.coef()[k], unrestricted.coef().get(k, float("nan")),
                                   f"[beta_constraints] coef {k}: unrestricted must recover plain offset model", delta=COEF_DELTA)
    pb = base.predict(df).as_data_frame()["p1"]
    pr = ro.predict(df).as_data_frame()["p1"]
    assert (pb - pr).abs().max() > 1e-6, "[beta_constraints] restricted predictions should differ"


# early_stopping can break the lambda loop mid-search; the restricted history must stay consistent and the
# unrestricted model must still recover the plain offset model at the same selected lambda.
def glm_remove_offset_lambda_search_early_stopping():
    df = _prostate_with_offset()
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    x, y = ["RACE", "DPROS", "PSA", "VOL", "GLEASON"], "CAPSULE"

    base = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                         early_stopping=True, seed=0xC0FFEE)
    base.train(x=x, y=y, training_frame=df, offset_column="off")
    ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, early_stopping=True,
                                       remove_offset_effects=True, seed=0xC0FFEE)
    ro.train(x=x, y=y, training_frame=df, offset_column="off")

    pyunit_utils.assert_equals(H2OGeneralizedLinearEstimator.getLambdaBest(base),
                               H2OGeneralizedLinearEstimator.getLambdaBest(ro),
                               "[early_stopping] lambda_best must match plain offset model", delta=LAMBDA_DELTA)
    unrestricted = ro.make_unrestricted_glm_model()
    for k in base.coef().keys():
        pyunit_utils.assert_equals(base.coef()[k], unrestricted.coef().get(k, float("nan")),
                                   f"[early_stopping] coef {k}: unrestricted must recover plain offset model", delta=COEF_DELTA)


# NOTE: the sparse-standardized-data case lives only in Java
# (GLMRemoveOffsetLambdaSearchTest.sparseDataWorksWithLambdaSearch). Forcing the sparse chunk path needs a
# genuinely sparse-encoded frame (Java TestFrameBuilder); a dense pandas/H2OFrame does not produce one.


# alpha=0 (ridge -> 30 lambdas) and multiple alphas (-> 100 lambdas each, alpha_best selection) must both
# work: same selected lambda and the unrestricted model recovers the plain offset model.
def glm_remove_offset_lambda_search_alpha():
    df = _prostate_with_offset()
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    x, y = ["RACE", "DPROS", "PSA", "VOL", "GLEASON"], "CAPSULE"
    for alpha in ([0.0], [0.1, 0.5, 0.9]):
        base = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, alpha=alpha, seed=0xC0FFEE)
        base.train(x=x, y=y, training_frame=df, offset_column="off")
        ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, alpha=alpha,
                                           remove_offset_effects=True, seed=0xC0FFEE)
        ro.train(x=x, y=y, training_frame=df, offset_column="off")

        pyunit_utils.assert_equals(H2OGeneralizedLinearEstimator.getLambdaBest(base),
                                   H2OGeneralizedLinearEstimator.getLambdaBest(ro),
                                   f"[alpha={alpha}] lambda_best must match plain offset model", delta=LAMBDA_DELTA)
        unrestricted = ro.make_unrestricted_glm_model()
        for k in base.coef().keys():
            pyunit_utils.assert_equals(base.coef()[k], unrestricted.coef().get(k, float("nan")),
                                       f"[alpha={alpha}] coef {k}: unrestricted model must recover plain offset model", delta=COEF_DELTA)


# learning_curve_plot is the advertised UX surface for this feature. Asserting only that a file was written
# proves nothing: savefig writes a PNG for empty axes, which is exactly the failure mode the derived model's
# submodel adoption prevents (the plot filters the scoring history by alpha_best, which is 0 on an unadopted
# placeholder submodel, so every row is dropped and the curve comes out empty).
def glm_remove_offset_lambda_search_plot():
    df = _prostate_with_offset()
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, remove_offset_effects=True,
                                       generate_scoring_history=True, seed=0xC0FFEE)
    ro.train(x=["RACE", "DPROS", "PSA", "VOL", "GLEASON"], y="CAPSULE", training_frame=df, offset_column="off")

    def assert_curve_not_empty(model, label, expect_unrestricted_series):
        fig = model.learning_curve_plot().figure()
        series = {}
        for ax in fig.axes:
            for line in ax.get_lines():
                series[line.get_label()] = line
        assert "Training" in series, "%s: no Training curve was plotted (labels: %r)" % (label, list(series))
        assert len(series["Training"].get_xdata()) > 1, \
            "%s: the Training curve is empty - the alpha_best filter dropped every scoring-history row" % label
        # only a trained model carries scoring_history_unrestricted_model; the derived model's history was copied
        # into its main slot, so it has no second series to plot
        assert ("Training (Unrestricted model)" in series) == expect_unrestricted_series, \
            "%s: unexpected presence/absence of the offset-included curve (labels: %r)" % (label, list(series))

    assert_curve_not_empty(ro, "trained model", True)
    # The derived model is the case the submodel adoption exists for: its scoring history is copied from the
    # source and then filtered by alpha_best, which is 0 unless the placeholder submodel was replaced.
    assert_curve_not_empty(ro.make_unrestricted_glm_model(), "derived model", False)


# A derived model reports the source's selected lambda/alpha (so learning_curve_plot has a non-empty curve),
# but it carries no regularization path of its own: its submodel is a placeholder, because the source's submodel
# betas are standardized while a derived output's DataInfo is NONE (its coefficients are already denormalized).
# Pinning both halves here so neither the retag nor the documented limitation regresses silently.
def glm_remove_offset_lambda_search_derived_lambda_best():
    cars, x, y, offset_col = _cars_with_offset()

    ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, remove_offset_effects=True,
                                       seed=0xC0FFEE)
    ro.train(x=x, y=y, training_frame=cars, offset_column=offset_col)
    unrestricted = ro.make_unrestricted_glm_model()

    pyunit_utils.assert_equals(H2OGeneralizedLinearEstimator.getLambdaBest(ro),
                               H2OGeneralizedLinearEstimator.getLambdaBest(unrestricted),
                               "derived model must report the source's lambda_best, not 0")
    # coefficients must stay in the raw (denormalized) space - adopting the source's standardized submodel
    # betas here would silently return standardized values
    for k in ro.coef().keys():
        pyunit_utils.assert_equals(ro.coef()[k], unrestricted.coef().get(k, float("nan")),
                                   f"derived model coefficient {k} must stay in the raw coefficient space")


# fold_column + lambda_search + remove_offset_effects is documented behaviour: the CV metric slots are still
# populated, but the per-lambda deviance_xval/deviance_se columns are omitted (they require nfolds) and the
# cross-validated lambda override is not applied.
def glm_remove_offset_lambda_search_fold_column():
    cars, x, y, offset_col = _cars_with_offset()
    folds = h2o.H2OFrame([[i % 3] for i in range(cars.nrows)])
    folds.set_names(["fold"])
    cars = cars.cbind(folds)

    ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, remove_offset_effects=True,
                                       fold_column="fold", generate_scoring_history=True, seed=0xC0FFEE)
    ro.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    assert ro._model_json["output"]["cross_validation_metrics_unrestricted_model"] is not None, \
        "a fold_column must still populate the unrestricted CV metric slot"
    history = ro.scoring_history()
    assert "deviance_xval" not in list(history.columns), \
        "the per-lambda deviance_xval column requires nfolds; it must be omitted with a fold_column"
    assert "deviance_se" not in list(history.columns), \
        "the per-lambda deviance_se column requires nfolds; it must be omitted with a fold_column"

    # the restricted history must still be the offset-removed one, and the unrestricted slot must still be filled
    unrestricted = ro.scoring_history_unrestricted_model
    assert unrestricted is not None and len(unrestricted.cell_values) > 0, \
        "a fold_column must still produce an unrestricted scoring history"
    dev_col = unrestricted.col_header.index("deviance_train")
    restricted_dev = list(history["deviance_train"])
    unrestricted_dev = [row[dev_col] for row in unrestricted.cell_values]
    assert any(abs(r - u) > 1e-6 for r, u in zip(restricted_dev, unrestricted_dev)), \
        "with a fold_column the main scoring_history must still be the offset-removed table"

    # note: lambda_best is deliberately not compared against an nfolds=0 model. The cross-validated *override* is
    # gated on nfolds > 1, but cv_computeAndSetOptimalParameters still truncates _parms._lambda for a fold_column,
    # so cross-validation does shape the grid the final pick is made over.


pyunit_utils.run_tests([
    glm_remove_offset_lambda_search,
    glm_remove_offset_lambda_search_offset_zeroed,
    glm_remove_offset_lambda_search_scoring_history,
    glm_remove_offset_lambda_search_scoring_history_no_gaps,
    glm_remove_offset_lambda_search_checkpoint_may_enable_lambda_search,
    glm_remove_offset_lambda_search_cross_validation,
    glm_remove_offset_lambda_search_mojo,
    glm_remove_offset_lambda_search_families,
    glm_remove_offset_lambda_search_weights,
    glm_remove_offset_lambda_search_validation,
    glm_remove_offset_lambda_search_beta_constraints,
    glm_remove_offset_lambda_search_early_stopping,
    glm_remove_offset_lambda_search_alpha,
    glm_remove_offset_lambda_search_plot,
    glm_remove_offset_lambda_search_derived_lambda_best,
    glm_remove_offset_lambda_search_fold_column,
])
