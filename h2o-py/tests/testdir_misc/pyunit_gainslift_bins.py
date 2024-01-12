import sys

sys.path.insert(1, "../../")
import h2o
import pandas as pd
from tests import pyunit_utils
from h2o.estimators import *
from h2o.utils.threading import local_context

eps = 1e-10


def fast_estimator(estimator, **kwargs):
    additional_args = dict(seed=123456)
    if estimator in (H2ORandomForestEstimator, H2OGradientBoostingEstimator, H2OXGBoostEstimator):
        additional_args["ntrees"] = 5
    if estimator == H2ODeepLearningEstimator:
        additional_args["hidden"] = [5]
    if estimator == H2OGeneralizedAdditiveEstimator:
        additional_args["gam_columns"] = ["age"]

    return estimator(**additional_args, **kwargs)


def ks_score(mod, data, y):
    with local_context(datatable_disabled=True, polars_disabled=True): # conversion h2o frame to pandas using single thread as before
      from scipy.stats import ks_2samp

      df = pd.DataFrame()
      df["label"] = data[y].as_data_frame().iloc[:, 0]
      df["probs"] = mod.predict(data)["p1"].as_data_frame().iloc[:, 0]

      label_0 = df[df["label"] == 0]
      label_1 = df[df["label"] == 1]

      ks = ks_2samp(label_0["probs"], label_1["probs"])

      return ks.statistic


def get_ks(model, data):
    """
    This is needed for getting the KS metric for the data.
    
    Using model.kolmogorov_smirnov() would work for most models for training data but not for
    DRF which reports OOB stats...
    """
    perf = model.model_performance(data)
    return max(perf.gains_lift()["kolmogorov_smirnov"])


def assert_eq(a, b):
    if abs(a - b) >= eps:
        print("Expected: {}, Actual: {}, diff: {}".format(b, a, a - b))
    return abs(a - b) < eps


def assert_not_eq(a, b):
    if abs(a - b) <= eps:
        print("Expected: {}, Actual: {}, diff: {}".format(b, a, a - b))
    return abs(a - b) > eps


def test_helper(Estimator):
    df = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"))
    y = "survived"
    df[y] = df[y].asfactor()

    mod_default = fast_estimator(Estimator)
    mod_default.train(y=y, training_frame=df)

    assert_not_eq(get_ks(mod_default, df), ks_score(mod_default, df, y))  # default histogram is not precise enough

    mod_glbins = fast_estimator(Estimator, gainslift_bins=df.nrow)
    mod_glbins.train(y=y, training_frame=df)

    assert_eq(get_ks(mod_glbins, df), ks_score(mod_glbins, df, y))  # should result in precise statistics


def test_deeplearning():
    test_helper(H2ODeepLearningEstimator)


def test_drf():
    test_helper(H2ORandomForestEstimator)


def test_gam():
    test_helper(H2OGeneralizedAdditiveEstimator)


def test_gbm():
    test_helper(H2OGradientBoostingEstimator)


def test_glm():
    test_helper(H2OGeneralizedLinearEstimator)


def test_xgboost():
    test_helper(H2OXGBoostEstimator)


def test_stacked_ensemble():
    df = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"))
    y = "survived"
    df[y] = df[y].asfactor()

    base_kwargs = dict(nfolds=3, keep_cross_validation_predictions=True)

    base_models = [
        fast_estimator(H2ORandomForestEstimator, **base_kwargs),
        fast_estimator(H2OGradientBoostingEstimator, **base_kwargs),
        fast_estimator(H2ODeepLearningEstimator, **base_kwargs),
        fast_estimator(H2OGeneralizedLinearEstimator, **base_kwargs),
    ]

    for est in base_models:
        est.train(y=y, training_frame=df)

    mod_default = fast_estimator(H2OStackedEnsembleEstimator, base_models=base_models)
    mod_default.train(y=y, training_frame=df)

    assert_not_eq(get_ks(mod_default, df), ks_score(mod_default, df, y))  # default histogram is not precise enough

    mod_glbins = fast_estimator(H2OStackedEnsembleEstimator, base_models=base_models, gainslift_bins=df.nrow)
    mod_glbins.train(y=y, training_frame=df)

    assert_eq(get_ks(mod_glbins, df), ks_score(mod_glbins, df, y))  # should result in precise statistics


pyunit_utils.run_tests([
    test_deeplearning,
    test_drf,
    test_gam,
    test_gbm,
    test_glm,
    test_xgboost,
    test_stacked_ensemble,
])
