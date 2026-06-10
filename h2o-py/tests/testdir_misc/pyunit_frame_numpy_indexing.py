#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
End-to-end tests for numpy-array indexing on H2OFrame (added for scikit-learn >= 1.6
``_safe_indexing`` interop):

  * ``fr[np.array([...])]`` selects ROWS (deliberate asymmetry with a Python list,
    which selects columns);
  * boolean masks select rows via ``np.flatnonzero``;
  * ndarrays inside a 2-tuple work for both row and column selectors;
  * ``fr[rows, ...]`` (Ellipsis) selects all columns;
  * 2-D and float arrays raise the documented errors.
"""
import sys

sys.path.insert(1, "../../")
import numpy as np

import h2o
from tests import pyunit_utils


def _make_frame():
    fr = h2o.H2OFrame({"a": [10, 11, 12, 13, 14],
                       "b": [20, 21, 22, 23, 24],
                       "c": [30, 31, 32, 33, 34]})
    assert fr.nrow == 5 and fr.ncol == 3
    return fr[sorted(fr.columns)]  # deterministic column order


def test_bare_int_ndarray_selects_rows():
    fr = _make_frame()
    sub = fr[np.array([0, 2, 4])]
    assert sub.nrow == 3 and sub.ncol == 3, (sub.nrow, sub.ncol)
    assert sub["a"].as_data_frame(use_pandas=False, header=False) == [["10"], ["12"], ["14"]]
    # ...whereas a plain Python list selects COLUMNS (documented asymmetry)
    sub_cols = fr[[0, 2]]
    assert sub_cols.ncol == 2 and sub_cols.nrow == 5, (sub_cols.ncol, sub_cols.nrow)


def test_bool_ndarray_selects_rows():
    fr = _make_frame()
    mask = np.array([True, False, True, False, True])
    sub = fr[mask]
    assert sub.nrow == 3, sub.nrow
    assert sub["a"].as_data_frame(use_pandas=False, header=False) == [["10"], ["12"], ["14"]]


def test_ndarray_in_tuple_rows_and_cols():
    fr = _make_frame()
    sub = fr[np.array([1, 3]), np.array([0, 2])]
    assert sub.nrow == 2 and sub.ncol == 2, (sub.nrow, sub.ncol)
    assert sub.columns == ["a", "c"], sub.columns
    assert sub["c"].as_data_frame(use_pandas=False, header=False) == [["31"], ["33"]]


def test_ndarray_rows_with_ellipsis_cols():
    fr = _make_frame()
    sub = fr[np.array([0, 1]), ...]
    assert sub.nrow == 2 and sub.ncol == 3, (sub.nrow, sub.ncol)


def test_two_dimensional_ndarray_raises():
    fr = _make_frame()
    try:
        fr[np.array([[0, 1], [2, 3]])]
        assert False, "expected ValueError for a 2-D index array"
    except ValueError as e:
        assert "1-D" in str(e), str(e)


def test_float_ndarray_raises_type_error():
    fr = _make_frame()
    try:
        fr[np.array([0.0, 2.0])]
        assert False, "expected TypeError for a float index array"
    except TypeError as e:
        assert "integer or boolean" in str(e), str(e)


def frame_numpy_indexing_suite():
    test_bare_int_ndarray_selects_rows()
    test_bool_ndarray_selects_rows()
    test_ndarray_in_tuple_rows_and_cols()
    test_ndarray_rows_with_ellipsis_cols()
    test_two_dimensional_ndarray_raises()
    test_float_ndarray_raises_type_error()


if __name__ == "__main__":
    pyunit_utils.standalone_test(frame_numpy_indexing_suite)
else:
    frame_numpy_indexing_suite()
