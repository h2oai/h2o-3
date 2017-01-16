#!/usr/bin/env python
from __future__ import division
import sys; sys.path.insert(1, "../..")
import h2o
from tests import pyunit_utils


def test_load_sparse():
    try:
        import scipy.sparse as sp
    except ImportError:
        return

    A = sp.csr_matrix([[1, 2, 0, 5.5], [0, 0, 3, 6.7], [4, 0, 5, 0]])
    fr = h2o.H2OFrame(A)
    assert fr.shape == (3, 4)
    assert fr.as_data_frame(False) == \
        [['C1', 'C2', 'C3', 'C4'], ['1', '2', '0', '5.5'], ['0', '0', '3', '6.7'], ['4', '0', '5', '0.0']]

    A = sp.lil_matrix((1000, 1000))
    A.setdiag(10)
    for i in range(999):
        A[i, i + 1] = -3
        A[i + 1, i] = -2
    fr = h2o.H2OFrame(A)
    assert fr.shape == (1000, 1000)
    means = fr.mean().getrow()
    assert means == [0.008] + [0.005] * 998 + [0.007]

    I = [0, 0, 1, 3, 1, 0, 0]
    J = [0, 2, 1, 3, 1, 0, 0]
    V = [1, 1, 1, 1, 1, 1, 1]
    B = sp.coo_matrix((V, (I, J)), shape=(4, 4))
    fr = h2o.H2OFrame(B)
    assert fr.shape == (4, 4)
    assert fr.as_data_frame(False) == [['C1', 'C2', 'C3', 'C4'], ['3', '0', '1', '0'], ['0', '2', '0', '0'],
                                       ['0', '0', '0', '0'], ['0', '0', '0', '1']]

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_load_sparse)
else:
    test_load_sparse()
