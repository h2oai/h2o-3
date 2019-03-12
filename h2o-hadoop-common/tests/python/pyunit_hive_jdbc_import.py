#! /usr/env/python

import sys
import os
import h2o
sys.path.insert(1, os.path.join("..", "..", "..", "h2o-py"))
from tests import pyunit_utils
from numpy import isclose

def adapt_airlines(airlines_dataset):
    airlines_dataset["table_for_h2o_import.origin"] = airlines_dataset["table_for_h2o_import.origin"].asfactor()
    airlines_dataset["table_for_h2o_import.fdayofweek"] = airlines_dataset["table_for_h2o_import.fdayofweek"].asfactor()
    airlines_dataset["table_for_h2o_import.uniquecarrier"] = airlines_dataset["table_for_h2o_import.uniquecarrier"].asfactor()
    airlines_dataset["table_for_h2o_import.dest"] = airlines_dataset["table_for_h2o_import.dest"].asfactor()
    airlines_dataset["table_for_h2o_import.fyear"] = airlines_dataset["table_for_h2o_import.fyear"].asfactor()
    airlines_dataset["table_for_h2o_import.fdayofmonth"] = airlines_dataset["table_for_h2o_import.fdayofmonth"].asfactor()
    airlines_dataset["table_for_h2o_import.isdepdelayed"] = airlines_dataset["table_for_h2o_import.isdepdelayed"].asfactor()
    airlines_dataset["table_for_h2o_import.fmonth"] = airlines_dataset["table_for_h2o_import.fmonth"].asfactor()
    return airlines_dataset

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
    airlines_dataset_original = h2o.import_file(path="https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip")

    # read from Hive Distributed
    if hive_dist_enabled:
        airlines_dataset_dist = h2o.import_sql_select(connection_url, select_query, username, password)
        airlines_dataset_dist = adapt_airlines(airlines_dataset_dist)
        pyunit_utils.compare_frames(airlines_dataset_original, airlines_dataset_dist, 100, tol_numeric=0)

    # read from Hive Streaming
    airlines_dataset_streaming = h2o.import_sql_select(connection_url, select_query, username, password, fetch_mode="SINGLE")
    airlines_dataset_streaming = adapt_airlines(airlines_dataset_streaming)
    pyunit_utils.compare_frames(airlines_dataset_original, airlines_dataset_streaming, 100, tol_numeric=0)


if __name__ == "__main__":
    pyunit_utils.standalone_test(hive_jdbc_import)
else:
    hive_jdbc_import()
