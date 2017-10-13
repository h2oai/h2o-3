from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.group_by import GroupBy
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type


def h2o_H2OFrame_group_by():
    """
    Python API test: h2o.frame.H2OFrame.group_by(by)

    Copied from pyunit_groupby.py
    """
    h2o_iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    grouped = h2o_iris.group_by(["class"])      # set by as a list
    assert_is_type(grouped, GroupBy)

    grouped = h2o_iris.group_by("class")        # set by as a str
    assert_is_type(grouped, GroupBy)

    grouped = h2o_iris.group_by(4)        # set by as an int
    assert_is_type(grouped, GroupBy)

    grouped = h2o_iris.group_by([4])        # set by as an int list
    assert_is_type(grouped, GroupBy)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_group_by())
else:
    h2o_H2OFrame_group_by()
