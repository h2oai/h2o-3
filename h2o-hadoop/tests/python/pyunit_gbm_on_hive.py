#! /usr/env/python

import sys, os
sys.path.insert(1, os.path.join("..","..","..", "h2o-py"))
from tests import pyunit_utils
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def gbm_on_hive():
    connection_url = "jdbc:hive2://localhost:10000/default"
    select_query = "select * from airlinestest"
    username = "hive"
    password = ""
    airlines_dataset_original = h2o.import_file(path="https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip")
    airlines_dataset = h2o.import_sql_select(connection_url, select_query, username, password)
    pyunit_utils.compare_frames(airlines_dataset_original, airlines_dataset, 100, tol_numeric=0)
    airlines_dataset["table_for_h2o_import.origin"] = airlines_dataset["table_for_h2o_import.origin"].asfactor()
    airlines_dataset["table_for_h2o_import.fdayofweek"] = airlines_dataset["table_for_h2o_import.fdayofweek"].asfactor()
    airlines_dataset["table_for_h2o_import.uniquecarrier"] = airlines_dataset["table_for_h2o_import.uniquecarrier"].asfactor()
    airlines_dataset["table_for_h2o_import.dest"] = airlines_dataset["table_for_h2o_import.dest"].asfactor()
    airlines_dataset["table_for_h2o_import.fyear"] = airlines_dataset["table_for_h2o_import.fyear"].asfactor()
    airlines_dataset["table_for_h2o_import.fdayofmonth"] = airlines_dataset["table_for_h2o_import.fdayofmonth"].asfactor()
    airlines_dataset["table_for_h2o_import.isdepdelayed"] = airlines_dataset["table_for_h2o_import.isdepdelayed"].asfactor()
    airlines_dataset["table_for_h2o_import.fmonth"] = airlines_dataset["table_for_h2o_import.fmonth"].asfactor()
    airlines_X_col_names = airlines_dataset.col_names[:-2]
    airlines_y_col_name = airlines_dataset.col_names[-2]
    train, valid, test = airlines_dataset.split_frame([0.6, 0.2], seed=1234)
    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    gbm_v1 = H2OGradientBoostingEstimator(model_id="gbm_airlines_v1", seed=2000000)
    gbm_v1.train(airlines_X_col_names, airlines_y_col_name, training_frame=train, validation_frame=valid)
    gbm_v1.predict(test)

if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_on_hive)
else:
    gbm_on_hive()