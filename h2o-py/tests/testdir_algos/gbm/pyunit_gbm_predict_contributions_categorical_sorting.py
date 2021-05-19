from __future__ import division
from builtins import range
import sys

from h2o.exceptions import H2OConnectionError, H2OResponseError, H2OServerError

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import operator


def gbm_predict_contributions_sorting():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    first_row = fr[0, :]

    fr["RACE"] = fr["RACE"].asfactor()
    m = H2OGradientBoostingEstimator(ntrees=10, seed=1234, categorical_encoding="one_hot_explicit")
    m.train(x=list(range(2, fr.ncol)), y=1, training_frame=fr)

    contributions = m.predict_contributions(fr, top_n=None, bottom_n=None, compare_abs="None")
    assert_equals(11, contributions.shape[1], "Wrong number of columns")
    assert_equals(380, contributions.shape[0], "Wrong number of rows")

    # Compute old version of contributions, sort them and compare to the sorted ones in Java
    contributions = m.predict_contributions(first_row)
    names = contributions.names[0:-1]
    values = list(map(float, contributions.as_data_frame(use_pandas=False, header=False)[0][0:-1]))
    values_abs = list(map(abs, values))
    contributions_iterator = zip(names, values)
    contributions_dictionary = dict(contributions_iterator)
    first_row_sorted_asc = sorted(contributions_dictionary.items(), key=operator.itemgetter(1))
    first_row_sorted_desc = sorted(contributions_dictionary.items(), key=operator.itemgetter(1), reverse=True)

    contributions_iterator_abs = zip(names, values_abs)
    contributions_dictionary_abs = dict(contributions_iterator_abs)
    first_row_sorted_asc_abs = sorted(contributions_dictionary_abs.items(), key=operator.itemgetter(1))
    first_row_sorted_desc_abs = sorted(contributions_dictionary_abs.items(), key=operator.itemgetter(1), reverse=True)

    contributions = m.predict_contributions(first_row, top_n=2, bottom_n=0, compare_abs=False)
    assert_equals(first_row_sorted_desc[0][0], contributions[0, 0], "Not correctly sorted")
    assert_equals(first_row_sorted_desc[1][0], contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=0, bottom_n=2, compare_abs=False)
    assert_equals(first_row_sorted_asc[0][0], contributions[0, 0], "Not correctly sorted")
    assert_equals(first_row_sorted_asc[1][0], contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=2, bottom_n=2, compare_abs=False)
    check_sorted_correcty_first_two_last_two(contributions, first_row_sorted_desc, first_row_sorted_asc)

    contributions = m.predict_contributions(first_row, top_n=-1, bottom_n=0, compare_abs=False)
    check_sorted_correctly(contributions, first_row_sorted_desc)

    contributions = m.predict_contributions(first_row, top_n=-1, bottom_n=-1, compare_abs=False)
    check_sorted_correctly(contributions, first_row_sorted_desc)

    contributions = m.predict_contributions(first_row, top_n=0, bottom_n=-1, compare_abs=False)
    check_sorted_correctly(contributions, first_row_sorted_asc)

    contributions = m.predict_contributions(first_row, top_n=50, bottom_n=-1, compare_abs=False)
    check_sorted_correctly(contributions, first_row_sorted_desc)

    contributions = m.predict_contributions(first_row, top_n=-1, bottom_n=50, compare_abs=False)
    check_sorted_correctly(contributions, first_row_sorted_desc)

    contributions = m.predict_contributions(first_row, top_n=50, bottom_n=50, compare_abs=False)
    check_sorted_correctly(contributions, first_row_sorted_desc)

    contributions = m.predict_contributions(first_row, top_n=6, bottom_n=5, compare_abs=False)
    check_sorted_correctly(contributions, first_row_sorted_desc)

    contributions = m.predict_contributions(fr, top_n=0, bottom_n=0, compare_abs=True)
    assert_equals(11, contributions.shape[1], "Wrong number of columns")
    assert_equals(380, contributions.shape[0], "Wrong number of rows")

    contributions = m.predict_contributions(first_row, top_n=2, bottom_n=0, compare_abs=True)
    assert_equals(first_row_sorted_desc_abs[0][0], contributions[0, 0], "Not correctly sorted")
    assert_equals(first_row_sorted_desc_abs[1][0], contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=0, bottom_n=2, compare_abs=True)
    assert_equals(first_row_sorted_asc_abs[0][0], contributions[0, 0], "Not correctly sorted")
    assert_equals(first_row_sorted_asc_abs[1][0], contributions[0, 2], "Not correctly sorted")

    contributions = m.predict_contributions(first_row, top_n=2, bottom_n=2, compare_abs=True)
    check_sorted_correcty_first_two_last_two(contributions, first_row_sorted_desc_abs, first_row_sorted_asc_abs)

    contributions = m.predict_contributions(first_row, top_n=-1, bottom_n=0, compare_abs=True)
    check_sorted_correctly(contributions, first_row_sorted_desc_abs)

    contributions = m.predict_contributions(first_row, top_n=-1, bottom_n=-1, compare_abs=True)
    check_sorted_correctly(contributions, first_row_sorted_desc_abs)

    contributions = m.predict_contributions(first_row, top_n=0, bottom_n=-1, compare_abs=True)
    check_sorted_correctly(contributions, first_row_sorted_asc_abs)

    contributions = m.predict_contributions(first_row, top_n=50, bottom_n=-1, compare_abs=True)
    check_sorted_correctly(contributions, first_row_sorted_desc_abs)

    contributions = m.predict_contributions(first_row, top_n=-1, bottom_n=50, compare_abs=True)
    check_sorted_correctly(contributions, first_row_sorted_desc_abs)

    contributions = m.predict_contributions(first_row, top_n=50, bottom_n=50, compare_abs=True)
    check_sorted_correctly(contributions, first_row_sorted_desc_abs)

    contributions = m.predict_contributions(first_row, top_n=6, bottom_n=5, compare_abs=True)
    check_sorted_correctly(contributions, first_row_sorted_desc_abs)


def check_sorted_correctly(contributions, python_sorted):
    assert_equals(21, contributions.shape[1], "Wrong number of columns")
    assert_equals(python_sorted[0][0], contributions[0, 0], "Not correctly sorted")
    assert_equals(python_sorted[1][0], contributions[0, 2], "Not correctly sorted")
    assert_equals(python_sorted[2][0], contributions[0, 4], "Not correctly sorted")
    assert_equals(python_sorted[3][0], contributions[0, 6], "Not correctly sorted")
    assert_equals(python_sorted[4][0], contributions[0, 8], "Not correctly sorted")
    assert_equals(python_sorted[5][0], contributions[0, 10], "Not correctly sorted")
    assert_equals(python_sorted[6][0], contributions[0, 12], "Not correctly sorted")
    assert_equals(python_sorted[7][0], contributions[0, 14], "Not correctly sorted")
    assert_equals(python_sorted[8][0], contributions[0, 16], "Not correctly sorted")
    assert_equals(python_sorted[9][0], contributions[0, 18], "Not correctly sorted")


def check_sorted_correcty_first_two_last_two(contributions, python_sorted_desc, python_sorted_asc):
    assert_equals(python_sorted_desc[0][0], contributions[0, 0], "Not correctly sorted")
    assert_equals(python_sorted_desc[1][0], contributions[0, 2], "Not correctly sorted")
    assert_equals(python_sorted_asc[0][0], contributions[0, 4], "Not correctly sorted")
    assert_equals(python_sorted_asc[1][0], contributions[0, 6], "Not correctly sorted")


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_predict_contributions_sorting)
else:
    gbm_predict_contributions_sorting()
