#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
import numpy as np
from h2o.frame import H2OFrame
from tests import pyunit_utils


def col_names_check():
    iris_wheader = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    expected_names = ["sepal_len", "sepal_wid", "petal_len", "petal_wid", "class"]
    assert iris_wheader.col_names == expected_names, \
        "Expected {0} for column names but got {1}".format(expected_names, iris_wheader.col_names)

    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    expected_names = ["C1", "C2", "C3", "C4", "C5"]
    assert iris.col_names == expected_names, \
        "Expected {0} for column names but got {1}".format(expected_names, iris.col_names)

    df = H2OFrame.from_python(np.random.randn(100, 4).tolist(), column_names=list("ABCD"), column_types=["enum"] * 4)
    df.head()
    expected_names = list("ABCD")
    assert df.col_names == expected_names, \
        "Expected {} for column names but got {}".format(expected_names, df.col_names)
    assert list(df.types.values()) == ["enum"] * 4, \
        "Expected {} for column types but got {}".format(["enum"] * 4, df.types)

    df = H2OFrame(np.random.randn(100, 4).tolist())
    df.head()
    expected_names = ["C1", "C2", "C3", "C4"]
    assert df.col_names == expected_names, \
        "Expected {} for column names but got {}".format(expected_names, df.col_names)
    assert list(df.types.values()) == ["real"] * 4, \
        "Expected {} for column types but got {}".format(["real"] * 4, df.types)

    df = H2OFrame({'B': ['a', 'a', 'b', 'NA', 'NA']})
    df.head()
    assert df.col_names == ["B"], "Expected {} for column names but got {}".format(["B"], df.col_names)

    df = H2OFrame.from_python({'B': ['a', 'a', 'b', 'NA', 'NA']}, column_names=["X"])
    df.head()
    assert df.col_names == ["X"], "Expected {} for column names but got {}".format(["X"], df.col_names)


if __name__ == "__main__":
    pyunit_utils.standalone_test(col_names_check)
else:
    col_names_check()
