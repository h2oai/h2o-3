from __future__ import division
from builtins import range
import sys

sys.path.insert(1, "../../../")
from tests import pyunit_utils, assert_equals
from h2o.estimators.xgboost import *


def gbm_predict_contributions_sorting_large():
    fr = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/creditcardfraud/creditcardfraud.csv"))

    m = H2OXGBoostEstimator(ntrees=10, seed=1234)
    m.train(x=list(range(0, fr.ncol)), y=30, training_frame=fr)

    contributions = m.predict_contributions(fr, top_n=-1, bottom_n=0, compare_abs=False)
    assert_equals(61, contributions.shape[1], "Wrong number of columns")
    assert_equals(284807, contributions.shape[0], "Wrong number of rows")


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_predict_contributions_sorting_large)
else:
    gbm_predict_contributions_sorting_large()
