#!/usr/env/python
import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o


def adapt_frame(dataset, table_name="table_for_h2o_import"):
    dataset[table_name + ".community_area_name"] = dataset[table_name + ".community_area_name"].asfactor()
    return dataset


def hive_jdbc_import():
    connection_url = "jdbc:hive2://localhost:10000/default"
    krb_enabled = os.getenv('KRB_ENABLED', 'false').lower() == 'true'
    use_token = os.getenv('KRB_USE_TOKEN', 'false').lower() == 'true'
    if krb_enabled:
        if use_token:
            connection_url += ";auth=delegationToken"
        else:
            connection_url += ";principal=%s" % os.getenv('HIVE_PRINCIPAL', 'hive/localhost@H2O.AI')
        
    hive_dist_enabled = os.getenv('HIVE_DIST_ENABLED', 'true').lower() == 'true'

    select_query = "select * from chicago"
    username = "hive"
    password = ""

    # read original
    dataset_original = h2o.import_file(path=pyunit_utils.locate("smalldata/chicago/chicagoCensus.csv"))

    if hive_dist_enabled:
        # read from Hive Distributed
        dataset_dist = h2o.import_sql_select(connection_url, select_query, username, password)
        dataset_dist = adapt_frame(dataset_dist)
        pyunit_utils.compare_frames_local(dataset_original, dataset_dist, prob=1)

    # read from Hive Streaming
    dataset_streaming = h2o.import_sql_select(connection_url, select_query, username, password, fetch_mode="SINGLE")
    dataset_streaming = adapt_frame(dataset_streaming)
    pyunit_utils.compare_frames_local(dataset_original, dataset_streaming, prob=1)

    # read from Hive without temp table
    dataset_no_temp_table = h2o.import_sql_select(connection_url, select_query, username, password, 
        use_temp_table = False, fetch_mode="SINGLE")
    print(dataset_no_temp_table)
    dataset_no_temp_table = adapt_frame(dataset_no_temp_table, "sub_h2o_import")
    pyunit_utils.compare_frames_local(dataset_original, dataset_no_temp_table, prob=1)

    # read from Hive with custom temp table
    dataset_custom_temp_table = h2o.import_sql_select(connection_url, select_query, username, password, 
        use_temp_table = True, temp_table_name = "user_database.test_import_table", fetch_mode="SINGLE")
    dataset_custom_temp_table = adapt_frame(dataset_custom_temp_table, "test_import_table")
    pyunit_utils.compare_frames_local(dataset_original, dataset_custom_temp_table, prob=1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(hive_jdbc_import)
else:
    hive_jdbc_import()
