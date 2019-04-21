#!/usr/bin/env python
import h2o
from tests import pyunit_utils

def test_set_name_with_name():
    fr = h2o.H2OFrame({'A': [1, 2, 3]})
    fr.set_name("A", "new_col_name")
    assert fr.names[0] == "new_col_name"


def test_set_name_with_index():
    fr = h2o.H2OFrame({'A': [1, 2, 3]})
    fr.set_name(0, "new_col_name")
    assert fr.names[0] == "new_col_name"


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_set_name_with_name)
    pyunit_utils.standalone_test(test_set_name_with_index)

else:
    test_set_name_with_name()
    test_set_name_with_index()

