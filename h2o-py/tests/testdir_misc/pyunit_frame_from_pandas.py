#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
import pandas as pd
from tests import pyunit_utils


def test_pandas_to_h2oframe():

    pddf = pd.DataFrame({"one": [4, 6, 1], "two": ["a", "b", "cde"], "three": [0, None, 1]})
    h2odf1 = h2o.H2OFrame.from_python(pddf)
    h2odf2 = h2o.H2OFrame.from_python(pddf, column_names=["A", "B", "C"])

    assert h2odf1.shape == pddf.shape
    assert h2odf1.columns == list(pddf.columns)
    assert h2odf2.shape == pddf.shape
    assert h2odf2.columns == ["A", "B", "C"]


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_pandas_to_h2oframe)
else:
    test_pandas_to_h2oframe()
