from __future__ import division
from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators.random_forest import H2ORandomForestEstimator


def rf_predict_contributions_sorting_smoke():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    first_row = fr[0, :]

    m = H2ORandomForestEstimator(ntrees=50, max_depth=100, keep_cross_validation_models=True, seed=1234)
    m.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    contributions = m.predict_contributions(fr, top_n=0, bottom_n=0, compare_abs=False)
    assert_equals(8, contributions.shape[1], "Wrong number of columns")
    assert_equals(380, contributions.shape[0], "Wrong number of rows")

    contributions = m.predict_contributions(first_row, top_n=2, bottom_n=0, compare_abs=False)
    assert_equals("VOL", contributions[0, 0], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=0, bottom_n=2, compare_abs=False)
    assert_equals("PSA", contributions[0, 0], "Not correctly sorted")
    assert_equals("GLEASON", contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=2, bottom_n=2, compare_abs=False)
    assert_equals("VOL", contributions[0, 0], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 2], "Not correctly sorted")
    assert_equals("PSA", contributions[0, 4], "Not correctly sorted")
    assert_equals("GLEASON", contributions[0, 6], "Not correctly sorted")

    contributions = m.predict_contributions(fr, top_n=0, bottom_n=0, compare_abs=True)
    assert_equals(8, contributions.shape[1], "Wrong number of columns")
    assert_equals(380, contributions.shape[0], "Wrong number of rows")

    contributions = m.predict_contributions(first_row, top_n=2, bottom_n=0, compare_abs=True)
    assert_equals("PSA", contributions[0, 0], "Not correctly sorted")
    assert_equals("GLEASON", contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=0, bottom_n=2, compare_abs=True)
    assert_equals("DCAPS", contributions[0, 0], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=2, bottom_n=2, compare_abs=True)
    assert_equals("PSA", contributions[0, 0], "Not correctly sorted")
    assert_equals("GLEASON", contributions[0, 2], "Not correctly sorted")
    assert_equals("DCAPS", contributions[0, 4], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 6], "Not correctly sorted")


if __name__ == "__main__":
    pyunit_utils.standalone_test(rf_predict_contributions_sorting_smoke)
else:
    rf_predict_contributions_sorting_smoke()
