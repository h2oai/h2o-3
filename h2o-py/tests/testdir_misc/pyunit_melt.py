#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Pyunit for h2o.melt"""
import sys
sys.path.insert(1,"../../")
import pandas as pd
from h2o.frame import H2OFrame
from tests import pyunit_utils


def melt_compare(df, **kwargs):
    frozen_h2o = H2OFrame(df)
    melted_h2o = frozen_h2o.melt(**kwargs)

    def sort(f):
        var_name = kwargs["var_name"] if "var_name" in kwargs else "variable"
        return f.sort_values(by=kwargs["id_vars"]+[var_name]).reset_index(drop=True)

    actual = sort(melted_h2o.as_data_frame())
    expected = sort(pd.melt(df, **kwargs))

    assert expected.equals(actual)


def test_melt():
    df = pd.DataFrame({'A': {0: 'a', 1: 'b', 2: 'c'},
                       'B': {0: 1, 2: 5},
                       'C': {0: 2, 1: 4, 2: 6}})

    melt_compare(df, id_vars=["A"], value_vars=["B"])
    melt_compare(df, id_vars=["A"], value_vars=["B", "C"])
    melt_compare(df, id_vars=["A"])
    melt_compare(df, id_vars=["A", "B"], value_vars=["C"])

    melt_compare(df, id_vars=["A"], value_vars=["B"], var_name="test_VARIABLE", value_name="test_VALUE")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_melt)
else:
    test_melt()
