import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame
import math


def h2o_H2OFrame_match():
    """
    Python API test: h2o.frame.H2OFrame.match(table, nomatch=0)

    """
    data = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    nomatch = 0
    
    # string value
    match_frame = data["C5"].match(['Iris-setosa'], nomatch=nomatch)
    assert_is_type(match_frame, H2OFrame)    # check return type
    assert match_frame.sum()[0, 0] == 50.0, "h2o.H2OFrame.match() command is not working."  # check return result
    assert match_frame[0, 0] == 1, "match value should be 1"
    assert match_frame[50, 0] == nomatch, "match value should be 0"
    assert match_frame[100, 0] == nomatch, "match value should be 0"

    # string values
    match_frame = data["C5"].match(['Iris-setosa', 'Iris-versicolor'], nomatch=nomatch)
    assert_is_type(match_frame, H2OFrame, nomatch=0)    # check return type
    assert round(match_frame.sum()[0, 0]) == 150, "h2o.H2OFrame.match() command is not working."  # check return result
    assert match_frame[0, 0] == 1, "match value should be 1"
    assert match_frame[50, 0] == 2, "match value should be 2"
    assert match_frame[100, 0] == nomatch, "match value should be 0"

    # use default nomatch
    match_values = ['Iris-setosa', 'Iris-versicolor', 'Iris-virginica']
    match_frame = data["C5"].match(match_values)
    assert_is_type(match_frame, H2OFrame)    # check return type
    assert round(match_frame.sum()[0, 0]) == 300, "h2o.H2OFrame.match() command is not working." 
    assert match_frame[0, 0] == 1, "match value should be 1"
    assert match_frame[50, 0] == 2, "match value should be 2"
    assert match_frame[100, 0] == 3, "match value should be 3"

    # set nomatch value to -1
    nomatch = -1
    match_values = ['Iris-setosa', 'Iris-versicolor']
    match_frame = data["C5"].match(match_values, nomatch=nomatch)
    assert round(match_frame.sum()[0, 0]) == 100, "h2o.H2OFrame.match() command is not working."
    assert match_frame[0, 0] == 1, "match value should be 1"
    assert match_frame[50, 0] == 2, "match value should be 2"
    assert match_frame[100, 0] == nomatch, "match value should be -1"

    # start index feature = 0
    match_values = ['Iris-setosa', 'Iris-versicolor']
    start_index = 0
    match_frame = data["C5"].match(match_values, start_index=start_index)
    assert match_frame.sum()[0, 0] == 50, "h2o.H2OFrame.match() command is not working."
    assert match_frame[0, 0] == start_index, "match value should be 0"
    assert match_frame[50, 0] == start_index+1, "match value should be 1"
    assert math.isnan(match_frame[100, 0]), "match value should be nan"

    # numeric values
    match_values = [5.1]
    match_frame = data["C1"].match(match_values)
    assert match_frame.sum()[0, 0] == 9, "h2o.H2OFrame.match() command is not working."
    assert match_frame[0, 0] == 1, "match value should be 1"
    assert match_frame[17, 0] == 1, "match value should be 1"
    assert match_frame[19, 0] == 1, "match value should be 1"
    assert math.isnan(match_frame[1, 0]), "match value should be nan"

    # duplicate in match values
    nomatch = 0
    match_frame = data["C5"].match(['Iris-setosa', 'Iris-versicolor', 'Iris-setosa'], nomatch=nomatch)
    assert_is_type(match_frame, H2OFrame, nomatch=0)    # check return type
    assert round(match_frame.sum()[0, 0]) == 150, "h2o.H2OFrame.match() command is not working."  # check return result
    assert match_frame[0, 0] == 1, "match value should be 1"
    assert match_frame[50, 0] == 2, "match value should be 2"
    assert match_frame[100, 0] == nomatch, "match value should be 0"
    
    # test example for doc
    data = h2o.import_file("h2o://iris")
    match_col = data['class'].match(['Iris-setosa', 'Iris-versicolor', 'Iris-setosa'])
    match_col.names = ['match']
    iris_match = data.cbind(match_col)
    sample = iris_match.split_frame(ratios=[0.05], seed=1)[0]
    print(sample)
    
    
pyunit_utils.standalone_test(h2o_H2OFrame_match)
