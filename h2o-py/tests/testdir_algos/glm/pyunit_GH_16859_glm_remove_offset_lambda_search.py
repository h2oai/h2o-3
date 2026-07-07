import sys
sys.path.insert(1, "../../../")
import tempfile
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


# GH-16859: remove_offset_effects must also work with lambda_search=True.
# remove_offset_effects does not change the fit (the offset is still part of the
# optimization), it only strips the offset contribution from the reported model.


def _cars_with_offset():
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset = h2o.H2OFrame([[.5]] * cars.nrows)
    offset.set_names(["offset"])
    return cars.cbind(offset), ["name", "power", "year"], "economy_20mpg", "offset"


# The model derived via make_unrestricted_glm_model must recover the plain offset-present model
# (same coefficients, predictions and selected lambda), while the reported predictions differ.
def glm_remove_offset_lambda_search():
    cars, x, y, offset_col = _cars_with_offset()

    glm_offset = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True, seed=0xC0FFEE)
    glm_offset.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    glm_ro = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                           remove_offset_effects=True, seed=0xC0FFEE)
    glm_ro.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    glm_unrestricted = glm_ro.make_unrestricted_glm_model(dest="unrestricted_ls")
    assert glm_unrestricted is not None, "make_unrestricted_glm_model returned None"

    preds_offset = glm_offset.predict(cars).as_data_frame()
    preds_ro = glm_ro.predict(cars).as_data_frame()
    preds_unrestricted = glm_unrestricted.predict(cars).as_data_frame()

    # unrestricted model must reproduce the offset-present model (same fitted beta)
    for k in glm_offset.coef().keys():
        pyunit_utils.assert_equals(glm_offset.coef()[k], glm_unrestricted.coef().get(k, float("nan")),
                                   f"Coefficient {k} differs between offset model and unrestricted model!")

    # lambda_search must select the same regularization strength (fit is identical)
    pyunit_utils.assert_equals(H2OGeneralizedLinearEstimator.getLambdaBest(glm_offset),
                               H2OGeneralizedLinearEstimator.getLambdaBest(glm_ro),
                               "Selected lambda_best differs between offset model and remove_offset model!")

    for i in range(preds_offset.shape[0]):
        pyunit_utils.assert_equals(preds_offset.iloc[i, 1], preds_unrestricted.iloc[i, 1],
                                   f"Prediction {i} should match offset-present model but doesn't!")

    # remove_offset_effects must actually change the reported predictions
    for i in range(preds_offset.shape[0]):
        pyunit_utils.assert_not_equal(preds_offset.iloc[i, 1], preds_ro.iloc[i, 1],
                                      f"Prediction {i} should differ once the offset effect is removed!")


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
                                   f"Prediction {i}: restricted model must equal offset-zeroed model!")


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

    # the unrestricted scoring history must match the plain offset model's scoring history
    # (compare every column except the non-deterministic timestamp/duration)
    cols_to_compare = [c for c in unrestricted.col_header if c not in ("timestamp", "duration")]
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(unrestricted, plain, cols_to_compare)

    assert glm_offset._model_json["output"]["scoring_history_unrestricted_model"] is None, \
        "Plain offset model should not have an unrestricted scoring history"


# remove_offset_effects + lambda_search + cross-validation must train and populate CV metrics.
def glm_remove_offset_lambda_search_cv():
    cars, x, y, offset_col = _cars_with_offset()

    glm_cv = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=True,
                                           remove_offset_effects=True, nfolds=3, seed=0xC0FFEE)
    glm_cv.train(x=x, y=y, training_frame=cars, offset_column=offset_col)

    assert glm_cv.model_performance(xval=True) is not None, \
        "remove_offset_effects + lambda_search + nfolds must populate cross-validation metrics"


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


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_remove_offset_lambda_search)
    pyunit_utils.standalone_test(glm_remove_offset_lambda_search_offset_zeroed)
    pyunit_utils.standalone_test(glm_remove_offset_lambda_search_scoring_history)
    pyunit_utils.standalone_test(glm_remove_offset_lambda_search_cv)
    pyunit_utils.standalone_test(glm_remove_offset_lambda_search_mojo)
else:
    glm_remove_offset_lambda_search()
    glm_remove_offset_lambda_search_offset_zeroed()
    glm_remove_offset_lambda_search_scoring_history()
    glm_remove_offset_lambda_search_cv()
    glm_remove_offset_lambda_search_mojo()
