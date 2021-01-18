from __future__ import division
from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def gbm_predict_contributions_sorting():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    first_row = fr[0, :]

    m = H2OGradientBoostingEstimator(nfolds=10, ntrees=10, keep_cross_validation_models=True, seed=1234)
    m.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    contributions = m.predict_contributions(fr, top_n=0, top_bottom_n=0, abs_val=False)
    assert_equals(8, contributions.shape[1], "Wrong number of columns")
    assert_equals(380, contributions.shape[0], "Wrong number of rows")

    contributions = m.predict_contributions(first_row, top_n=2, top_bottom_n=0, abs_val=False)
    assert_equals("VOL", contributions[0, 0], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=0, top_bottom_n=2, abs_val=False)
    assert_equals("GLEASON", contributions[0, 0], "Not correctly sorted")
    assert_equals("PSA", contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=2, top_bottom_n=2, abs_val=False)
    assert_equals("VOL", contributions[0, 0], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 2], "Not correctly sorted")
    assert_equals("GLEASON", contributions[0, 4], "Not correctly sorted")
    assert_equals("PSA", contributions[0, 6], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=-1, top_bottom_n=0, abs_val=False)
    check_sorted_correctly(contributions)

    contributions = m.predict_contributions(first_row, top_n=-1, top_bottom_n=-1, abs_val=False)
    check_sorted_correctly(contributions)

    contributions = m.predict_contributions(first_row, top_n=0, top_bottom_n=-1, abs_val=False)
    check_sorted_correctly_reverse(contributions)

    contributions = m.predict_contributions(first_row, top_n=50, top_bottom_n=-1, abs_val=False)
    check_sorted_correctly(contributions)

    contributions = m.predict_contributions(first_row, top_n=-1, top_bottom_n=50, abs_val=False)
    check_sorted_correctly(contributions)

    contributions = m.predict_contributions(first_row, top_n=50, top_bottom_n=50, abs_val=False)
    check_sorted_correctly(contributions)

    contributions = m.predict_contributions(first_row, top_n=4, top_bottom_n=4, abs_val=False)
    check_sorted_correctly(contributions)

    contributions = m.predict_contributions(fr, top_n=0, top_bottom_n=0, abs_val=True)
    assert_equals(1, contributions.shape[1], "Wrong number of columns")
    assert_equals(380, contributions.shape[0], "Wrong number of rows")

    contributions = m.predict_contributions(first_row, top_n=2, top_bottom_n=0, abs_val=True)
    assert_equals("GLEASON", contributions[0, 0], "Not correctly sorted")
    assert_equals("PSA", contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=0, top_bottom_n=2, abs_val=True)
    assert_equals("RACE", contributions[0, 0], "Not correctly sorted")
    assert_equals("DCAPS", contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=2, top_bottom_n=2, abs_val=True)
    assert_equals("GLEASON", contributions[0, 0], "Not correctly sorted")
    assert_equals("PSA", contributions[0, 2], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 4], "Not correctly sorted")
    assert_equals("DCAPS", contributions[0, 6], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=-1, top_bottom_n=0, abs_val=True)
    check_sorted_correctly_abs(contributions)

    contributions = m.predict_contributions(first_row, top_n=-1, top_bottom_n=-1, abs_val=True)
    check_sorted_correctly_abs(contributions)

    contributions = m.predict_contributions(first_row, top_n=0, top_bottom_n=-1, abs_val=True)
    check_sorted_correctly_abs_reverse(contributions)

    contributions = m.predict_contributions(first_row, top_n=50, top_bottom_n=-1, abs_val=True)
    check_sorted_correctly_abs(contributions)

    contributions = m.predict_contributions(first_row, top_n=-1, top_bottom_n=50, abs_val=True)
    check_sorted_correctly_abs(contributions)

    contributions = m.predict_contributions(first_row, top_n=50, top_bottom_n=50, abs_val=True)
    check_sorted_correctly_abs(contributions)

    contributions = m.predict_contributions(first_row, top_n=4, top_bottom_n=4, abs_val=True)
    check_sorted_correctly_abs(contributions)


def check_sorted_correctly(contributions):
    assert_equals(15, contributions.shape[1], "Wrong number of columns")
    assert_equals("VOL", contributions[0, 0], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 2], "Not correctly sorted")
    assert_equals("DCAPS", contributions[0, 4], "Not correctly sorted")
    assert_equals("DPROS", contributions[0, 6], "Not correctly sorted")
    assert_equals("AGE", contributions[0, 8], "Not correctly sorted")
    assert_equals("PSA", contributions[0, 10], "Not correctly sorted")
    assert_equals("GLEASON", contributions[0, 12], "Not correctly sorted")


def check_sorted_correctly_reverse(contributions):
    assert_equals(15, contributions.shape[1], "Wrong number of columns")
    assert_equals("VOL", contributions[0, 12], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 10], "Not correctly sorted")
    assert_equals("DCAPS", contributions[0, 8], "Not correctly sorted")
    assert_equals("DPROS", contributions[0, 6], "Not correctly sorted")
    assert_equals("AGE", contributions[0, 4], "Not correctly sorted")
    assert_equals("PSA", contributions[0, 2], "Not correctly sorted")
    assert_equals("GLEASON", contributions[0, 0], "Not correctly sorted")


def check_sorted_correctly_abs(contributions):
    assert_equals(15, contributions.shape[1], "Wrong number of columns")
    assert_equals("GLEASON", contributions[0, 0], "Not correctly sorted")
    assert_equals("PSA", contributions[0, 2], "Not correctly sorted")
    assert_equals("VOL", contributions[0, 4], "Not correctly sorted")
    assert_equals("AGE", contributions[0, 6], "Not correctly sorted")
    assert_equals("DPROS", contributions[0, 8], "Not correctly sorted")
    assert_equals("DCAPS", contributions[0, 10], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 12], "Not correctly sorted")


def check_sorted_correctly_abs_reverse(contributions):
    assert_equals(15, contributions.shape[1], "Wrong number of columns")
    assert_equals("GLEASON", contributions[0, 12], "Not correctly sorted")
    assert_equals("PSA", contributions[0, 10], "Not correctly sorted")
    assert_equals("VOL", contributions[0, 8], "Not correctly sorted")
    assert_equals("AGE", contributions[0, 6], "Not correctly sorted")
    assert_equals("DPROS", contributions[0, 4], "Not correctly sorted")
    assert_equals("DCAPS", contributions[0, 2], "Not correctly sorted")
    assert_equals("RACE", contributions[0, 0], "Not correctly sorted")


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_predict_contributions_sorting)
else:
    gbm_predict_contributions_sorting()
