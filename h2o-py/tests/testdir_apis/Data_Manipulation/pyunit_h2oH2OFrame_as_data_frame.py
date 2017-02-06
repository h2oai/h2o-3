from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from pandas import DataFrame

def h2o_H2OFrame_as_data_frame():
    """
    Python API test: h2o.frame.H2OFrame.as_data_frame(use_pandas=True, header=True)

    Copied from pyunit_as_data_frame.py
    """
    smallbike = h2o.import_file(pyunit_utils.locate("smalldata/jira/citibike_head.csv"))
    smallbike_noheader = smallbike.as_data_frame(use_pandas=True, header=False)
    assert_is_type(smallbike_noheader, DataFrame)
    assert len(smallbike_noheader) == smallbike.nrow

    head_small_bike = smallbike.head(rows=5, cols=2)
    tail_small_bike = smallbike.tail(rows=5, cols=2)
    assert len(head_small_bike[0])==len(tail_small_bike[0])==5, "h2o.H2OFrame.as_data_frame() command is " \
                                                                "not working."
    assert len(head_small_bike)==len(tail_small_bike)==5, "h2o.H2OFrame.as_data_frame() command is not working."

    ##use_pandas = True
    small_bike_pandas = smallbike.as_data_frame(use_pandas=True, header=True)
    assert_is_type(small_bike_pandas, DataFrame)
    assert small_bike_pandas.shape == (smallbike.nrow, smallbike.ncol)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_as_data_frame())
else:
    h2o_H2OFrame_as_data_frame()
