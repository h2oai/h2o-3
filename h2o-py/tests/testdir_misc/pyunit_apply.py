#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
import pandas as pd
from pandas.util.testing import assert_frame_equal
import numpy as np
from functools import partial

def h2o_to_float(h2o, pd):
    """
    The method transform h2o result into a frame of floats. It is used as assert helper
    to compare with Pandas results.
    :return:
    """
    return (h2o.astype(float), pd)


def pd_to_int(h2o, pd):
    return (h2o, pd.apply(lambda x: 1 if x else 0))

#
# List of operators which are usable in lambda expression
# as parameter of `apply` function.
#
# Structure: '<name of op> : [h2o expression,
#                             supported axis,
#                             corresponding pandas expression or None if it is same as h2o expression,
#                             assert transformer or None to use default
#                             ]
#
# Note (1): not all operators support all directions.
#
# Note (2): some of operators produces differently typed results than Pandas which is used for results
# validation. Hence, each record can specify transformation before assert is invoked.
#
# Operators N:1 - transform a vector to a number
#
OPS_VEC_TO_SCALAR = {
    "mean": [lambda x: x.mean(), [0,1], None, None],
    "median": [lambda x: x.median(), [0], None, h2o_to_float],
    "max": [lambda x: x.max(), [0,1], None, h2o_to_float],
    "min": [lambda x: x.min(), [0,1], None, h2o_to_float],
    "sd": [lambda x: x.sd(), [0], lambda x: x.std(), None],
    "nacnt": [lambda x: x.nacnt(), [0], lambda x: sum(x.isnull()), None],
}

# Operators N:N - are applied on each element in vector
OPS_VEC_TO_VEC = {
    "adhoc-fce" : [lambda col: (col * col - col * 5 * col).abs() - 3.14, [0], None, None],
    "abs" : [lambda col: col.abs(), [0], None, None],
    "cos" : [lambda col: col.cos(), [0], lambda col: np.cos(col), None],
    "sin" : [lambda col: col.sin(), [0], lambda col: np.sin(col), None],
    "cosh" : [lambda col: col.cosh(), [0], lambda col: np.cosh(col), None],
    "exp" : [lambda col: col.exp(), [0], lambda col: np.exp(col), None],
    "sqrt" : [lambda col: col.sqrt(), [0], lambda col: np.sqrt(col), h2o_to_float],
    "tan" : [lambda col: col.tan(), [0], lambda col: np.tan(col), None],
    "tanh" : [lambda col: col.tanh(), [0], lambda col: np.tanh(col), h2o_to_float],
    "ceil" : [lambda col: col.ceil(), [0], lambda col: np.ceil(col), h2o_to_float],
    "floor" : [lambda col: col.floor(), [0], lambda col: np.floor(col), h2o_to_float],
    "log" : [lambda col: col.log(), [], lambda col: np.log(col), None],
    "select" : [lambda x: x["PSA"], [1], None, None],
    "select2": [lambda x: x['PSA'] > x['VOL'], [1], None, pd_to_int],
    "select3": [lambda x: 1 if x['PSA'] > x['VOL'] else 0, [], None, None]
}


def datafile():
    return pyunit_utils.locate("smalldata/logreg/prostate.csv")


def h2o_frame_fixture():
    return h2o.import_file(datafile())


def pandas_frame_fixture():
    return pd.read_csv(datafile())


def test_ops(fr, pf, ops_map):
    for axis in range(0,2):
        tester = partial(test_lambda, fr, pf, axis=axis)
        for name, op in ops_map.items():
            fce, supported_axes, pandas_fce, assert_transf = op
            assert_fce = get_assert_fce_for_axis(axis, assert_transf)
            op_desc = "Op '{}' (axis={}) ".format(name, axis)
            print(op_desc, end='')
            if axis not in supported_axes:
                print("UNSUPPORTED")
            else:
                tester(fce=fce, pandas_fce=pandas_fce, assert_fce=assert_fce)
                print("OK")


def pyunit_apply_n_to_1_ops():
    # H2O Frame
    fr = h2o_frame_fixture()
    # And Pandas DataFrame
    pf = pandas_frame_fixture()

    test_ops(fr, pf, OPS_VEC_TO_SCALAR)


def pyunit_apply_n_to_n_ops():
    # H2O Frame
    fr = h2o_frame_fixture()
    # And Pandas DataFrame
    pf = pandas_frame_fixture()

    test_ops(fr, pf, OPS_VEC_TO_VEC)


def pyunit_apply_with_args():
    fr = h2o_frame_fixture()
    ref = fr.scale(center=False, scale=False).as_data_frame()

    #vars
    false = False
    args = (False, False)
    kwargs = dict(center=False, scale=False)
    partial_args = (False,)
    partial_kwargs = dict(scale=False)

    to_test = dict(
        scale_with_arg=lambda x: x.scale(False, False),
        scale_with_kwarg=lambda x: x.scale(center=False, scale=False),
        scale_with_argkwarg=lambda x: x.scale(False, scale=False),
        scale_with_global_arg=(lambda x: x.scale(false, scale=false)),
        scale_with_args=(lambda x: x.scale(*args)),
        scale_with_kwargs=(lambda x: x.scale(**kwargs)),
        scale_with_partial_args=(lambda x: x.scale(False, *partial_args)),
        scale_with_partial_kwargs=(lambda x: x.scale(False, **partial_kwargs)),
        scale_with_partial_kwargs2=(lambda x: x.scale(center=False, **partial_kwargs)),
        scale_with_args_and_kwargs=(lambda x: x.scale(*partial_args, **partial_kwargs)),
        scale_with_all_kind_args=(lambda x: x.scale(False, *partial_args, scale=False, **partial_kwargs)),  # surprisingly this works because our signature verification is not that strict, but it's fine... at least behaves as expected.
    )
    for test, lbd in to_test.items():
        if lbd:
            print(test)
            res = fr.apply(lbd)
            res_df = res.as_data_frame()
            assert_frame_equal(res_df, ref)
            h2o.remove(res)



def test_lambda(h2o_frame, panda_frame, fce, axis, assert_fce, pandas_fce=None):
    h2o_result = h2o_frame.apply(fce, axis=axis).as_data_frame()
    pd_result = panda_frame.apply(pandas_fce if pandas_fce else fce, axis=axis)

    assert_fce(h2o_result, pd_result)


def get_assert_fce_for_axis(axis, assert_transf=None):
    assert_fce = __AXIS_ASSERTS__[axis]
    if assert_transf:
        return lambda h2o,pd: assert_fce(*assert_transf(h2o,pd))
    else:
        return assert_fce


def assert_row_equal(h2o_result, pd_result):
    if type(pd_result) is pd.core.frame.Series:
        pd_result = pd_result.to_frame(h2o_result.columns[0])
    assert_frame_equal(h2o_result, pd_result)


def assert_column_equal(h2o_result, pd_result):
    if type(pd_result) is pd.core.frame.Series:
        pd_result = pd_result.to_frame().transpose()
    assert_frame_equal(h2o_result, pd_result)


__AXIS_ASSERTS__ = { 0: assert_column_equal, 1: assert_row_equal}

__TESTS__ = [pyunit_apply_n_to_n_ops, pyunit_apply_n_to_1_ops, pyunit_apply_with_args]

if __name__ == "__main__":
    for func in __TESTS__:
        pyunit_utils.standalone_test(func)
else:
    for func in __TESTS__:
        func()
