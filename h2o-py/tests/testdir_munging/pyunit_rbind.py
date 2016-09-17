#!/usr/bin/python
# -*- encoding: utf-8 -*-
import h2o
from h2o.exceptions import H2OValueError
from tests import pyunit_utils


def rbind_check():
    """Test H2OFrame.rbind() function."""

    frame1 = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars.csv"))
    nrows1 = frame1.nrow

    frame2 = frame1.rbind(frame1)
    nrows2 = frame2.nrow
    assert nrows2 == 2 * nrows1

    frame3 = frame2.rbind(frame2)
    nrows3 = frame3.nrow
    assert nrows3 == 4 * nrows1

    frame4 = h2o.H2OFrame({"a": [1, 2, 3, 4, 5]})
    frame5 = frame4.rbind([frame4] * 9)
    assert frame5.nrow == frame4.nrow * 10

    try:
        iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
        frame1.rbind(iris)
        assert False, "Expected the rbind of cars and iris to fail, but it didn't"
    except H2OValueError:
        pass

    try:
        frame6 = h2o.H2OFrame({"a": [1.1, 1.2, 1.3]})
        frame4.rbind(frame6)
        assert False, "Expected the rbind of vecs of different types to fail"
    except H2OValueError:
        pass

    try:
        frame7 = h2o.H2OFrame({"b": [1, 2, 3, 4, 5]})
        frame4.rbind(frame7)
        assert False, "Expected the rbind of vecs with different names to fail"
    except H2OValueError:
        pass

    frame8 = h2o.H2OFrame({"a": [-1, -2, -3]})
    frame9 = frame4.rbind(frame8)
    frameA = frame8.rbind(frame4)
    assert frame9.nrow == frameA.nrow == frame4.nrow + frame8.nrow


if __name__ == "__main__":
    pyunit_utils.standalone_test(rbind_check)
else:
    rbind_check()
