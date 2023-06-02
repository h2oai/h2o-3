from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils, assert_equals, assert_not_equal
from h2o.estimators import H2ORandomForestEstimator, H2OGradientBoostingEstimator, H2OXGBoostEstimator


def assert_that_zero_contribution_has_zero_variable_importance(contributions, varipm):
    print(contributions)
    print(varipm)
    
    for name in varipm.variable.tolist():
        if contributions[0, name] == 0:
            assert_equals(varipm[varipm["variable"] == name]["relative_importance"].iloc[0], 0)
        else:
            assert_not_equal(varipm[varipm["variable"] == name]["relative_importance"].iloc[0], 0)


def not_used_col_has_zero_contribution():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    first_row = fr[0, :]

    drf = H2ORandomForestEstimator(ntrees=1, max_depth=1, seed=1234)
    drf.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    gbm = H2OGradientBoostingEstimator(ntrees=1, max_depth=1, seed=1234)
    gbm.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    xgb = H2OXGBoostEstimator(ntrees=1, max_depth=1, seed=1234)
    xgb.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)
    
    print("DRF")
    assert_that_zero_contribution_has_zero_variable_importance(drf.predict_contributions(first_row), drf.varimp(use_pandas=True))
    print("GBM")
    assert_that_zero_contribution_has_zero_variable_importance(gbm.predict_contributions(first_row), gbm.varimp(use_pandas=True))
    print("XGB")
    assert_that_zero_contribution_has_zero_variable_importance(xgb.predict_contributions(first_row), xgb.varimp(use_pandas=True))


if __name__ == "__main__":
    pyunit_utils.standalone_test(not_used_col_has_zero_contribution)
else:
    not_used_col_has_zero_contribution()
