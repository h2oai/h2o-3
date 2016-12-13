#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function
from collections import OrderedDict

import h2o
from tests import pyunit_utils


def test_isna():
    nan = float("nan")
    frame = h2o.H2OFrame.from_python(OrderedDict([
        ("A", [1, 0, 3, 4, 8, 4, 7]),
        ("B", [2, nan, -1, nan, nan, 9, 0]),
        ("C", ["one", "", "two", "", "seventeen", "1", ""]),
        ("D", ["oneteen", "", "twoteen", "", "sixteen", "twenteen", ""])
    ]), na_strings=[""], column_types={"C": "enum", "D": "string"})

    assert frame.shape == (7, 4)
    assert frame.names == ["A", "B", "C", "D"]
    assert frame.types == {"A": "int", "B": "int", "C": "enum", "D": "string"}, "Actual types: %r" % frame.types

    isna = frame.isna()
    rc = h2o.connection().requests_count
    assert isna.shape == (7, 4)
    assert isna.names == ["isNA(A)", "isNA(B)", "isNA(C)", "isNA(D)"]
    # at some point we'll switch to 'bool' column type
    assert isna.types == {"isNA(A)": "int", "isNA(B)": "int", "isNA(C)": "int", "isNA(D)": "int"}, \
        "Actual types: %r" % isna.types
    assert h2o.connection().requests_count == rc, "Frame isna should not be evaluated yet!"

    print()
    print(isna)

    assert isna.shape == (7, 4)
    assert isna.names == ["isNA(A)", "isNA(B)", "isNA(C)", "isNA(D)"]
    assert isna.types == {"isNA(A)": "int", "isNA(B)": "int", "isNA(C)": "int", "isNA(D)": "int"}

    df = isna.as_data_frame(use_pandas=False, header=False)
    assert df == [
        ["0", "0", "0", "0"],
        ["0", "1", "1", "1"],
        ["0", "0", "0", "0"],
        ["0", "1", "1", "1"],
        ["0", "1", "0", "0"],
        ["0", "0", "0", "0"],
        ["0", "0", "1", "1"],
    ]



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_isna)
else:
    test_isna()

