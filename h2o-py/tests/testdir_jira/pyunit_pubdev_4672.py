#!/usr/bin/env python
import h2o
from tests import pyunit_utils
import numpy as np


def test_4672():
    # Compute means in numpy
    width = 4
    data = data_fixture(100, width)
    data_means = np.mean(data, axis=0)
    # Now upload data to H2O and compute means with H2O Rapids lambda
    fr = h2o.H2OFrame.from_python(data.tolist(), column_names=list("ABCD"))
    # If this didn't throw an exception in Python 3.6, then we're good.
    h2o_means = fr.apply(lambda x: x.mean(skipna=False))
    # Explicitly check that all columns means are equal
    assert all([abs(data_means[i] - h2o_means[0, i]) < 1e-12 for i in
                range(0, width)]), "Numpy and H2O column means need to match"


def data_fixture(height=100, width=4):
    return np.random.randn(height, width)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_4672)
else:
    test_4672()
