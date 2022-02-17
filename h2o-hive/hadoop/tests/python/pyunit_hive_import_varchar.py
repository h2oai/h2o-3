#! /usr/env/python

import sys
import os
import h2o
sys.path.insert(1, os.path.join("..", "..", "..", "..", "h2o-py"))
from h2o.utils.typechecks import (assert_is_type)
from h2o.frame import H2OFrame
from tests import pyunit_utils

def hive_import_varchar():
    connection_url = "jdbc:hive2://localhost:10000/default"
    krb_enabled = os.getenv('KRB_ENABLED', 'false').lower() == 'true'
    if krb_enabled:
        connection_url += ";auth=delegationToken"

    # import from regular table that contains VARCHAR(x) specification
    test_table_normal = h2o.import_hive_table("default", "AirlinesTest")
    assert_is_type(test_table_normal, H2OFrame)
    assert test_table_normal.nrow > 0

    # import from regular table JDBC that contains VARCHAR(x) specification
    test_table_normal = h2o.import_hive_table(connection_url, "AirlinesTest")
    assert_is_type(test_table_normal, H2OFrame)
    assert test_table_normal.nrow > 0


if __name__ == "__main__":
    pyunit_utils.standalone_test(hive_import_varchar)
else:
    hive_import_varchar()
