import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame


def h2o_H2OFrame_match():
    """
    Python API test: h2o.frame.H2OFrame.match(table, nomatch=0)

    Copied from runit_lstrip.R
    """
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    
    match_frame = iris["C5"].match(['Iris-setosa'])
    assert_is_type(match_frame, H2OFrame)    # check return type
    assert match_frame.sum()[0, 0] == 50.0, "h2o.H2OFrame.match() command is not working."  # check return result

    match_frame = iris["C5"].match(['Iris-setosa', 'Iris-versicolor'])
    assert_is_type(match_frame, H2OFrame)    # check return type
    assert match_frame.sum()[0, 0] == 100.0, "h2o.H2OFrame.match() command is not working."  # check return result

    match_frame = iris["C5"].match(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica'])
    assert_is_type(match_frame, H2OFrame)    # check return type
    assert match_frame.sum()[0, 0] == 150.0, "h2o.H2OFrame.match() command is not working."  # check return result

    match_frame = iris["C5"].match(['Iris-setosa'])
    assert_is_type(match_frame, H2OFrame)    # check return type
    assert match_frame.sum()[0, 0] == 50.0, "h2o.H2OFrame.match() command is not working."  # check return result
    


pyunit_utils.standalone_test(h2o_H2OFrame_match)
