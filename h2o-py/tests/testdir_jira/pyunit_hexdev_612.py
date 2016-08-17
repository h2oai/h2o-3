#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Test for HEXDEV-612."""
from tests import pyunit_utils
import h2o
import numpy as np

def test_hd612():
    """Test whether explicitly providing ``column_names`` to ``H2OFrame.from_python()`` produces an extra row."""
    data = np.array([i for i in range(40)]).reshape(10, 4)

    df = h2o.H2OFrame.from_python(data)
    assert df.nrow == 10
    assert df.ncol == 4
    assert df.names == ["C1", "C2", "C3", "C4"]

    names = ["spam", "egg", "ham", "milk"]
    df = h2o.H2OFrame.from_python(data, column_names=names)
    assert df.nrow == 10
    assert df.ncol == 4
    assert df.names == names

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_hd612)
else:
    test_hd612()
