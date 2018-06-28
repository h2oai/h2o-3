#!/usr/bin/env python
import h2o
from tests import pyunit_utils

"""
Checking corner cases when initializing H2OFrame

"""

def test_new_empty_frame():
    fr = h2o.H2OFrame()
    assert not fr._has_content()
    fr.describe()  # just checking no exception is raised

def test_new_frame_with_empty_list():
    fr = h2o.H2OFrame([])
    assert_empty(fr)
    fr.describe()  # just checking no exception is raised

def test_new_frame_with_empty_tuple():
    fr = h2o.H2OFrame(())
    assert_empty(fr)
    fr.describe()  # just checking no exception is raised

def test_new_frame_with_empty_nested_list():
    fr = h2o.H2OFrame([[]])
    assert_empty(fr)
    fr.describe()  # just checking no exception is raised

def test_new_frame_with_empty_dict():
    fr = h2o.H2OFrame({})
    assert_empty(fr)
    fr.describe()  # just checking no exception is raised

def assert_empty(frame):
    assert frame._has_content()
    assert frame.nrows == 0
    assert frame.ncols == 1

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_new_empty_frame)
    pyunit_utils.standalone_test(test_new_frame_with_empty_list)
    pyunit_utils.standalone_test(test_new_frame_with_empty_tuple)
    pyunit_utils.standalone_test(test_new_frame_with_empty_nested_list)
    pyunit_utils.standalone_test(test_new_frame_with_empty_dict)

else:
    test_new_empty_frame()
    test_new_frame_with_empty_list()
    test_new_frame_with_empty_tuple()
    test_new_frame_with_empty_nested_list()
    test_new_frame_with_empty_dict()

