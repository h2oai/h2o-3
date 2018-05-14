from __future__ import print_function
from builtins import range
import sys
import time
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.estimators.xgboost import *
#----------------------------------------------------------------------
# Purpose:  This test runs xgboost on the full airlines dataset.
#----------------------------------------------------------------------

def hdfs_xgboost_airlines():
    assert H2OXGBoostEstimator.available() is True  # check to make sure XGBoost is available

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()
        hdfs_file = "/datasets/airlines_all.csv"

        print("Import airlines_all.csv from HDFS")
        url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_file)
        airlines_h2o = h2o.import_file(url)
        n = airlines_h2o.nrow
        print("rows: {0}".format(n))

        print("Run Multinode XGBoost")
        myX = list(airlines_h2o.col_names)
        myX.remove("IsDepDelayed")
        y = "IsDepDelayed"

        h2oParams = {"ntrees":100, "max_depth":10, "seed":987654321, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                 "min_rows" : 5, "score_tree_interval": 100, 'booster':'gbtree', 'seed':12345}
        # parameters used to train XGBoost, should be the same as in native and H2O
        h2o_model = H2OXGBoostEstimator(**h2oParams)
        h2o_model.train(x=myX, y=y, training_frame=airlines_h2o)

        # get scoring time
        startTime = time.time()
        h2o_prediction = h2o_model.predict(airlines_h2o)
        processTime = time.time()-startTime
        print("Time (ms) taken to perform training is {0} and time (s) taking to perform scoring is "
              "{1}".format(h2o_model._model_json["output"]['run_time'], processTime))

        # compare H2O performance with native XGBoost performance.  Since I do not know how to run XGBoost in
        # hadoop, I will try to save some python performance and try to compare with that one.

    else:
        raise EnvironmentError



if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_xgboost_airlines)
else:
    hdfs_xgboost_airlines()
