#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Test suite for H2OTwoDimTable class."""
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.exceptions import H2OTypeError
from h2o.two_dim_table import H2OTwoDimTable


def test_table():
    """Test functionality of the H2OTwoDimTable class."""
    tbl1 = H2OTwoDimTable(cell_values=[[1, 2, 3], [10, 20, 30]], col_header=list("ABC"))
    tbl1.show()

    print()
    tbl2 = H2OTwoDimTable(cell_values=[[1, 2, 4]] * 10, col_header=["q1", "q2", "q3"], row_header=range(10),
                          table_header="Table 2")
    tbl2.show()

    assert tbl2["q1"] == [1] * 10
    assert tbl2["q2"] == [2] * 10
    assert tbl2["q3"] == [4] * 10
    assert tbl2[0] == [1] * 10
    assert tbl2[-1] == [4] * 10
    assert tbl2[[0, 1]] == [[1] * 10, [2] * 10]
    assert tbl2[["q3"]] == [[4] * 10]

    try:
        H2OTwoDimTable(cell_values=[[1, 2, 3, 4], [1, 2, 3]])
    except H2OTypeError:
        pass


test_table()
