#! /usr/env/python

import sys
import os
import h2o
sys.path.insert(1, os.path.join("..", "..", "..", "h2o-py"))
from h2o.utils.typechecks import (assert_is_type)
from h2o.frame import H2OFrame
from tests import pyunit_utils

def hive_import():
    # import from empty table should fail
    try:
        h2o.import_hive_table("default", "test_table_empty")
        assert false, "import_hive_table did not fail on empty table"
    except Exception as e:
        assert 'Nothing to import' in e.args[0].msg, "import_hive_table unexpected exception for empty table"

    # import from empty partitioned table should fail
    try:
        h2o.import_hive_table("default", "test_table_part_empty")
        assert false, "import_hive_table did not fail on empty table"
    except Exception as e:
        assert 'Nothing to import' in e.args[0].msg, "import_hive_table unexpected exception for empty table"

    # import from regular table
    test_table_normal = h2o.import_hive_table("default", "test_table_normal")
    assert_is_type(test_table_normal, H2OFrame)
    assert test_table_normal.nrow==3, "test_table_normal number of rows is incorrect. h2o.import_hive_table() is not working."
    assert test_table_normal.ncol==5, "test_table_normal number of columns is incorrect. h2o.import_hive_table() is not working."

    # import from partitioned table with multi format should fail
    try:
        h2o.import_hive_table("default", "test_table_multi_format")
        assert false, "import_hive_table did not fail on multi-format partitioned table"
    except Exception as e:
        assert 'allow_multi_format' in e.args[0].msg, "import_hive_table unexpected exception for multi-format table"

    # import from partitioned table with multi format enabled
    test_table_multi_format = h2o.import_hive_table("default", "test_table_multi_format", allow_multi_format=True)
    assert_is_type(test_table_multi_format, H2OFrame)
    assert test_table_multi_format.nrow==3, "test_table_multi_format number of rows is incorrect. h2o.import_hive_table() is not working."
    assert test_table_multi_format.ncol==5, "test_table_multi_format number of columns is incorrect. h2o.import_hive_table() is not working."

    # import from partitioned table with single format and partition filter
    test_table_multi_key = h2o.import_hive_table("default", "test_table_multi_key", partitions=[["2017", "2"]])
    assert_is_type(test_table_multi_key, H2OFrame)
    assert test_table_multi_key.nrow==3, "test_table_multi_key number of rows is incorrect. h2o.import_hive_table() is not working."
    assert test_table_multi_key.ncol==5, "test_table_multi_key number of columns is incorrect. h2o.import_hive_table() is not working."

    # import from partitioned table with single format and special characters in partition names
    test_table_escaping = h2o.import_hive_table("default", "test_table_escaping")
    assert_is_type(test_table_multi_key, H2OFrame)
    assert test_table_escaping.nrow==8, "test_table_escaping number of rows is incorrect. h2o.import_hive_table() is not working."
    assert test_table_escaping.ncol==2, "test_table_escaping number of columns is incorrect. h2o.import_hive_table() is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(hive_import)
else:
    hive_import()
