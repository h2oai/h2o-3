#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from tests import pyunit_utils
import h2o
from h2o.frame import H2OFrame
import numpy

def test_sw_602_endpoints_equality():
    data = [numpy.arange(0, 50000).tolist() for x in numpy.arange(0, 99).tolist()]
    fr = h2o.H2OFrame(data)
    full = H2OFrame.get_frame(fr.frame_id)
    light = H2OFrame.get_frame(fr.frame_id, light=True)

    assert full._ex._cache._id == light._ex._cache._id
    assert full._ex._cache._nrows == light._ex._cache._nrows
    assert full._ex._cache._ncols == light._ex._cache._ncols
    assert full._ex._cache._names == light._ex._cache._names
    assert full._ex._cache._data == light._ex._cache._data
    assert full._ex._cache._l == light._ex._cache._l

__TESTS__ = [test_sw_602_endpoints_equality]

if __name__ == "__main__":
    for func in __TESTS__:
        pyunit_utils.standalone_test(func)
else:
    for func in __TESTS__:
        func()
