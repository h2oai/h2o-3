from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.frame import H2OFrame
from h2o.utils.typechecks import assert_is_type


def h2o_H2OFrame_hist():
    """
    Python API test: h2o.frame.H2OFrame.hist(breaks='sturges', plot=True, **kwargs)

    Copied from pyunit_hist.py
    """
    df = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    df.describe()

    h = df[0].hist(breaks=5,plot=False)
    assert_is_type(h, H2OFrame)  # check return type
    assert h.nrow == 5, "h2o.H2OFrame.hist() command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_hist())
else:
    h2o_H2OFrame_hist()
