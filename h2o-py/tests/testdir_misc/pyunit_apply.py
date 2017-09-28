#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import division, print_function
import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
import pandas as pd
from pandas.util.testing import assert_frame_equal
import numpy as np
from functools import partial


#
# List of operators which are usable in lambda expression
# as parameter of `apply` function.
#
# Structure: '<name of op> : [h2o expression, supported axis, corresponding pandas expression or
#                             None if it is same as h2o expression]
#
#
# Operators N:1 - transform a vector to a number
#
OPS_VEC_TO_SCALAR = {
    "mean": [lambda x: x.mean(), [0,1], None],
    "median": [lambda x: x.median(), [0,1], None],
    "max": [lambda x: x.max(), [0,1], None],
    "min": [lambda x: x.min(), [0,1], None],
    "sd": [lambda x: x.sd(), [0,1], None],
    "nacnt": [lambda x: x.nacnt(), [0,1], None],
}

# Operators N:N - are applied on each element in vector
OPS_VEC_TO_VEC = {
    "adhoc-fce" : [lambda col: (col * col - col * 5 * col).abs() - 3.14, [0,1], None],
    "abs" : [lambda col: col.abs(), [0,1], None],
    "cos" : [lambda col: col.cos(), [0,1], lambda col: np.cos(col)],
    "sin" : [lambda col: col.sin(), [0,1], lambda col: np.sin(col)],
    "cosh" : [lambda col: col.cosh(), [0,1], lambda col: np.cosh(col)],
    "exp" : [lambda col: col.exp(), [0,1], lambda col: np.exp(col)],
    "sqrt" : [lambda col: col.sqrt(), [0,1], lambda col: np.sqrt(col)],
    "tan" : [lambda col: col.tan(), [0,1], lambda col: np.tan(col)],
    "tanh" : [lambda col: col.tanh(), [0,1], lambda col: np.tanh(col)],
    "ceil" : [lambda col: col.ceil(), [0,1], lambda col: np.ceil(col)],
    "floor" : [lambda col: col.floor(), [0,1], lambda col: np.floor(col)],
    "log" : [lambda col: col.log(), [0,1], lambda col: np.log(col)],
    "select" : [lambda x: x["PSA"], [1], None],
    "select2": [lambda x: x['PSA'] > x['VOL'], [1], None]
}


def datafile():
    return pyunit_utils.locate("smalldata/logreg/prostate.csv")


def h2o_frame_fixture():
    return h2o.import_file(datafile())


def pandas_frame_fixture():
    return pd.read_csv(datafile())


def pyunit_apply_n_to_1_ops():
    # H2O Frame
    fr = h2o_frame_fixture()
    # And Pandas DataFrame
    pf = pandas_frame_fixture()

    for axis in range(0,1):
        tester = partial(test_lambda, fr, pf, axis=axis)
        assert_fce = __AXIS_ASSERTS__[axis]
        for name, op in OPS_VEC_TO_SCALAR.items():
            fce, supported_axes, pandas_fce = op
            if axis not in supported_axes:
                print("Op '{}' does not support axis={}".format(name, axis))
            else:
                tester(fce=fce, pandas_fce=pandas_fce, assert_fce=assert_fce)

def pyunit_apply_on_row(): # axis = 1
    # H2O Frame
    fr = h2o_frame_fixture()
    # And Pandas DataFrame
    pf = pandas_frame_fixture()

    test = partial(test_lambda, fr, pf, axis=1)

    # Select all rows in PCA column:
    test(lambda x: x["PSA"],
         assert_fce=assert_row_equal)

    # Generate logical vector identifying rows where x['PSA'] > x['VOL']
    test(lambda x: x['PSA'] > x['VOL'],
         assert_fce=lambda h, p: assert_row_equal(h, p.astype(int)))


def pyunit_apply_on_row_failing():
    fr = h2o_frame_fixture()
    # And Pandas DataFrame
    pf = pandas_frame_fixture()

    test = partial(test_lambda, fr, pf, axis=1)

    # Generate binary vector identifying rows where x['PSA'] > x['VOL']
    test(lambda x: 1 if x['PSA'] > x['VOL'] else 0,
         assert_fce=assert_row_equal)

    #  Error: Expected a Frame but found a class water.rapids.vals.ValRow
    test(lambda x: x.median(),
         assert_fce=assert_row_equal)

def pyunit_apply_on_column_failing():
    fr = h2o_frame_fixture()
    # And Pandas DataFrame
    pf = pandas_frame_fixture()

    test = partial(test_lambda, fr, pf, axis=0)

    test(lambda x: x.median(),
         assert_fce=assert_column_equal)


def pyunit_apply_on_column(): # axis = 0
    # H2O Frame
    fr = h2o_frame_fixture()
    # And Pandas DataFrame
    pf = pandas_frame_fixture()

    test = partial(test_lambda, fr, pf, axis=0)

    test(lambda x: x.mean(),
         assert_fce=assert_column_equal)

def pyunit_apply_on_elements(): # axis does not matter
    # H2O Frame
    fr = h2o_frame_fixture()
    # And Pandas DataFrame
    pf = pandas_frame_fixture()

    test = partial(test_lambda, fr, pf, axis=0)

    test(fce=lambda col: (col * col - col * 5 * col).abs() - 3.14,
         assert_fce=assert_all_equal)

    test(fce=lambda col: col.abs(),
         assert_fce=assert_all_equal)

    test(fce=lambda col: col.cos(),
         pandas_fce=lambda col: np.cos(col),
         assert_fce=assert_all_equal)

    test(fce=lambda col: col.sin(),
         pandas_fce=lambda col: np.sin(col),
         assert_fce=assert_all_equal)

    test(fce=lambda col: col.cosh(),
         pandas_fce=lambda col: np.cosh(col),
         assert_fce=assert_all_equal)

    test(fce=lambda col: col.exp(),
         pandas_fce=lambda col: np.exp(col),
         assert_fce=assert_all_equal)

    # Improperly handle infinity
    #test(fce=lambda col: col.log(),
    #     pandas_fce=lambda col: np.log(col),
    #     assert_fce=assert_all_equal)

    test(fce=lambda col: col.sqrt(),
         pandas_fce=lambda col: np.sqrt(col),
         assert_fce=assert_all_equal)

    test(fce=lambda col: col.tan(),
         pandas_fce=lambda col: np.tan(col),
         assert_fce=assert_all_equal)

    test(fce=lambda col: col.tanh(),
         pandas_fce=lambda col: np.tanh(col),
         assert_fce=assert_all_equal)

    test(fce=lambda col: col.ceil(),
         pandas_fce=lambda col: np.ceil(col),
         assert_fce=assert_all_equal)

    test(fce=lambda col: col.floor(),
         pandas_fce=lambda col: np.floor(col),
         assert_fce=assert_all_equal)


def test_lambda(h2o_frame, panda_frame, name, fce, axis, assert_fce, pandas_fce=None):
    h2o_result = h2o_frame.apply(fce, axis=axis)
    pd_result = panda_frame.apply(pandas_fce if pandas_fce else fce, axis=axis)

    assert_fce(h2o_result, pd_result)


def assert_row_equal(h2o_result, pd_result):
    assert_frame_equal(h2o_result.as_data_frame(), pd_result.to_frame(h2o_result.names[0]))


def assert_column_equal(h2o_result, pd_result):
    assert_frame_equal(h2o_result.as_data_frame(), pd_result.to_frame().transpose())


def assert_all_equal(h2o_result, pd_result):
    h2o_as_pd = h2o_result.as_data_frame()
    try:
        assert_frame_equal(h2o_as_pd, pd_result)
    except AssertionError as e:
        print("H2O", h2o_as_pd)
        print("Pandas", pd_result)
        raise e

__AXIS_ASSERTS__ = { 0: assert_column_equal, 1: assert_row_equal}

__TESTS__ = [pyunit_apply_on_column, pyunit_apply_on_row]
__TESTS__ = [pyunit_apply_on_row]
__TESTS__ = [pyunit_apply_on_elements]
__TESTS__ = [pyunit_apply_on_column, pyunit_apply_on_row, pyunit_apply_on_elements]

__FAILING_TESTS__ = [pyunit_apply_on_row_failing, pyunit_apply_on_column_failing]

__TESTS__ = [pyunit_apply_n_to_1_ops]

if __name__ == "__main__":
    for func in __TESTS__:
        pyunit_utils.standalone_test(func)
else:
    for func in __TESTS__:
        func()
