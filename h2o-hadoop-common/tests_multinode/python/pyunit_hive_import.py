#! /usr/env/python

import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o

def adapt_frame(dataset, table_name="table_for_h2o_import"):
    dataset[table_name + ".community_area_name"] = dataset[table_name + ".community_area_name"].asfactor()
    return dataset

def hive_import():
    connection_url = "jdbc:hive2://localhost:10000/default"
    krb_enabled = os.getenv('KRB_ENABLED', 'false').lower() == 'true'
    if krb_enabled:
        connection_url += ";auth=delegationToken"
        
    # read original
    dataset_original = h2o.import_file(path=pyunit_utils.locate("smalldata/chicago/chicagoCensus.csv"))

    # read SELECT from Hive JDBC Select
    select_jdbc = h2o.import_sql_select(connection_url, "select * from chicago", "", "", fetch_mode="SINGLE")
    select_jdbc = adapt_frame(select_jdbc)
    pyunit_utils.compare_frames_local(dataset_original, select_jdbc, prob=1)

    # read TABLE from Hive JDBC Table
    table_jdbc = h2o.import_sql_select(connection_url, "chicago", "", "", fetch_mode="SINGLE")
    table_jdbc = adapt_frame(table_jdbc)
    pyunit_utils.compare_frames_local(dataset_original, table_jdbc, prob=1)

    # read TABLE from Hive FS
    table_direct = h2o.import_hive_table(connection_url, "chicago")
    table_direct = adapt_frame(table_direct)
    pyunit_utils.compare_frames_local(dataset_original, table_direct, prob=1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(hive_import)
else:
    hive_import()
