from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type

def h2o_H2OFrame_filter_na_cols():
    """
    Python API test: h2o.frame.H2OFrame.filter_na_cols(frac=0.2)

    copied from pyunit_filter_nacols.py
    """
    fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    include_cols = fr.filter_na_cols()  # should be all columns
    assert_is_type(include_cols, list)
    assert include_cols==list(range(fr.ncol)), "h2o.H2OFrame.filter_na_cols() command is not working."

    fr[1,1] = None  # make a value None, filter out the second column

    include_cols = fr.filter_na_cols(0.001)
    assert_is_type(include_cols, list)
    assert (1 not in include_cols) and (len(include_cols)==(fr.ncol-1)), \
        "h2o.H2OFrame.filter_na_cols() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_filter_na_cols())
else:
    h2o_H2OFrame_filter_na_cols()
