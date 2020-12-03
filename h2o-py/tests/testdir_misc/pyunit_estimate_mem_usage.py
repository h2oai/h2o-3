from __future__ import print_function
import sys
sys.path.insert(1, "../../")
from tests import pyunit_utils
from h2o import estimate_mem_usage


def test_estimate_mem_usage():
    k = 1024
    m = k * k
    g = k * m
    assert 1 == estimate_mem_usage(ncols=1, nrows=1)
    assert 1 == estimate_mem_usage(k, k)
    assert 33 == estimate_mem_usage(k, m)
    assert 2 == estimate_mem_usage(15, 3 * m)
    assert 1 == estimate_mem_usage(15, 3 * m, cat_cols=10)
    assert 2 == estimate_mem_usage(15, 3 * m, num_cols=10)
    assert 3 == estimate_mem_usage(15, 3 * m, uuid_cols=10)
    assert 2 == estimate_mem_usage(15, 3 * m, time_cols=10)
    assert 16 == estimate_mem_usage(15, 3 * m, string_cols=10)
    assert 4801 == estimate_mem_usage(15, 5 * g, string_cols=1)
    assert 1329 == estimate_mem_usage(20, 2 * g, string_cols=1, cat_cols=19)


def test_estimate_mem_usage_fail_wrong_params():
    try:
        estimate_mem_usage(20, 122324, string_cols=11, cat_cols=11)
        assert False
    except ValueError as e:
        assert "There can not be more specific columns then columns in total" == str(e)
        
    try:
        estimate_mem_usage(20, 122324, string_cols=-1, cat_cols=11)
        assert False
    except ValueError as e:
        assert "string_cols can't be a negative number" == str(e)
        
    try:
        estimate_mem_usage(20, 122324, cat_cols=-1)
        assert False
    except ValueError as e:
        assert "cat_cols can't be a negative number" == str(e)
        
    try:
        estimate_mem_usage(20, 122324, time_cols=-1, cat_cols=11)
        assert False
    except ValueError as e:
        assert "time_cols can't be a negative number" == str(e)
        
    try:
        estimate_mem_usage(20, 122324, string_cols=1, uuid_cols=-11)
        assert False
    except ValueError as e:
        assert "uuid_cols can't be a negative number" == str(e)
        
    try:
        estimate_mem_usage(20, 122324, num_cols=-1, cat_cols=11)
        assert False
    except ValueError as e:
        assert "num_cols can't be a negative number" == str(e)
        
    try:
        estimate_mem_usage(20, -123)
        assert False
    except ValueError as e:
        assert "nrows can't be a negative number" == str(e)

    try:
        estimate_mem_usage(-20, 123)
        assert False
    except ValueError as e:
        assert "ncols can't be a negative number" == str(e)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_estimate_mem_usage)
    pyunit_utils.standalone_test(test_estimate_mem_usage_fail_wrong_params)
else:
    test_estimate_mem_usage()
    test_estimate_mem_usage_fail_wrong_params()

