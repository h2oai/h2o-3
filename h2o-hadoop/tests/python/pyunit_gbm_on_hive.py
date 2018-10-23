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

def gbm_on_hive():
    connection_url = "jdbc:hive2://localhost:10000/default"
    krb_enabled = os.getenv('KRB_ENABLED', 'false')
    if krb_enabled.lower() == 'true':
        connection_url += ";principal=%s" % os.getenv('HIVE_PRINCIPAL', 'hive/localhost@H2O.AI')

    select_query = "select * from airlinestest"
    username = "hive"
    password = ""

    # read from S3
    airlines_dataset_original = h2o.import_file(path="https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip")
    # read from Hive Distributed
    airlines_dataset = h2o.import_sql_select(connection_url, select_query, username, password)
    airlines_dataset = adapt_airlines(airlines_dataset)
    # read from Hive Streaming
    airlines_dataset_streaming = h2o.import_sql_select(connection_url, select_query, username, password,
                                                       fetch_mode="SINGLE")
    airlines_dataset_streaming = adapt_airlines(airlines_dataset_streaming)

    # datasets should be identical from user's point of view
    pyunit_utils.compare_frames(airlines_dataset_original, airlines_dataset, 100, tol_numeric=0)
    pyunit_utils.compare_frames(airlines_dataset_original, airlines_dataset_streaming, 100, tol_numeric=0)

    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    airlines_X_col_names = airlines_dataset.col_names[:-2]
    airlines_y_col_name = airlines_dataset.col_names[-2]
    gbm_v1 = H2OGradientBoostingEstimator(model_id="gbm_airlines_v1", seed=2000000)
    gbm_v1.train(airlines_X_col_names, airlines_y_col_name,
                 training_frame=airlines_dataset, validation_frame=airlines_dataset_streaming)
    print(gbm_v1)
    # demonstrates that metrics can be slightly different due to different chunking on the backend
    assert isclose(gbm_v1.auc(train=True), gbm_v1.auc(valid=True), rtol=1e-4)


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_on_hive)
else:
    gbm_on_hive()