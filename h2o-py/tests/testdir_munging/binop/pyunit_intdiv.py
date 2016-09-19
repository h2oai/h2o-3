#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from h2o.exceptions import H2OTypeError, H2OValueError
from tests import pyunit_utils


def test_intdiv():
    """Test H2OFrame.__intdiv__()."""

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    iris = iris[:, 0:4]
    rows, cols = iris.shape

    # frame / scalar
    res = iris // 5
    assert res.shape == (rows, cols)

    res = 5 // iris[:, 0:2]
    assert res.shape == (rows, 2)

    # frame / col
    res = iris // iris[0]
    assert res.shape == (rows, cols)

    res = iris[2] // iris
    assert res.shape == (rows, 1)

    # col / col
    res = iris[0] // iris[1]
    res.show()

    res = iris[1] // iris[2] // iris[1]
    res.show()

    # col / scalar
    res = iris[0] // 5
    res.show()

    try:
        iris[0] // [1, 3]
        assert False
    except H2OTypeError:
        pass

    # frame / frame
    res = iris[:, 0:2] // iris[:, 0:2]
    assert res.shape == (150, 2)

    try:
        iris[:, 0:3] // iris[:, 0:2]
        assert False, "expected error. frames are different dimensions."
    except H2OValueError:
        pass



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_intdiv)
else:
    test_intdiv()
