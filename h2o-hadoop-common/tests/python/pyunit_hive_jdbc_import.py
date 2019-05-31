#! /usr/env/python

import sys
import os
import h2o
sys.path.insert(1, os.path.join("..", "..", "..", "h2o-py"))
from tests import pyunit_utils
from numpy import isclose

def adapt_airlines(dataset, table_name="table_for_h2o_import"):
    dataset[table_name + ".origin"] = dataset[table_name + ".origin"].asfactor()
    dataset[table_name + ".fdayofweek"] = dataset[table_name + ".fdayofweek"].asfactor()
    dataset[table_name + ".uniquecarrier"] = dataset[table_name + ".uniquecarrier"].asfactor()
    dataset[table_name + ".dest"] = dataset[table_name + ".dest"].asfactor()
    dataset[table_name + ".fyear"] = dataset[table_name + ".fyear"].asfactor()
    dataset[table_name + ".fdayofmonth"] = dataset[table_name + ".fdayofmonth"].asfactor()
    dataset[table_name + ".isdepdelayed"] = dataset[table_name + ".isdepdelayed"].asfactor()
    dataset[table_name + ".fmonth"] = dataset[table_name + ".fmonth"].asfactor()
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

    select_query = "select * from airlinestest"
    username = "hive"
    password = ""

    # read from S3
    dataset_original = h2o.import_file(path="https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip")

    if hive_dist_enabled:
        # read from Hive Distributed
        dataset_dist = h2o.import_sql_select(connection_url, select_query, username, password)
        dataset_dist = adapt_airlines(dataset_dist)
        pyunit_utils.compare_frames(dataset_original, dataset_dist, 100, tol_numeric=0)

    # read from Hive Streaming
    dataset_streaming = h2o.import_sql_select(connection_url, select_query, username, password, fetch_mode="SINGLE")
    dataset_streaming = adapt_airlines(dataset_streaming)
    pyunit_utils.compare_frames(dataset_original, dataset_streaming, 100, tol_numeric=0)

    # read from Hive without temp table
    dataset_no_temp_table = h2o.import_sql_select(connection_url, select_query, username, password, 
        use_temp_table = False, fetch_mode="SINGLE")
    print(dataset_no_temp_table)
    dataset_no_temp_table = adapt_airlines(dataset_no_temp_table, "sub_h2o_import")
    pyunit_utils.compare_frames(dataset_original, dataset_no_temp_table, 100, tol_numeric=0)

    # read from Hive with custom temp table
    dataset_custom_temp_table = h2o.import_sql_select(connection_url, select_query, username, password, 
        use_temp_table = True, temp_table_name = "user_database.test_import_table", fetch_mode="SINGLE")
    dataset_custom_temp_table = adapt_airlines(dataset_custom_temp_table, "test_import_table")
    pyunit_utils.compare_frames(dataset_original, dataset_custom_temp_table, 100, tol_numeric=0)


if __name__ == "__main__":
    pyunit_utils.standalone_test(hive_jdbc_import)
else:
    hive_jdbc_import()
