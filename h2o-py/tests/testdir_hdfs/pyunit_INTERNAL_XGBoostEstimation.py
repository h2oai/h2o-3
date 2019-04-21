from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.xgboost import H2OXGBoostEstimator
#----------------------------------------------------------------------
# Purpose:  Smoke-test basic XGBoost operation under Hadoop.
#----------------------------------------------------------------------

def createData(nrows, ncols):
    hdfs_name_node = pyunit_utils.hadoop_namenode()
    hdfs_airlines_file = "/datasets/airlines_all.05p.csv"

    url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_airlines_file)
    airlines = h2o.import_file(url)

    myX = ["Year", "Month", "DayofMonth", "DayOfWeek", "Distance"]
    myY = "IsDepDelayed"

    allCols = list(myX)
    allCols.append(myY)

    airlines = airlines[allCols]

    num_new_features = ncols - airlines.ncol
    sample_data = h2o.create_frame(rows = nrows, cols = num_new_features, categorical_fraction = 0,
                                  seed = 1234, seed_for_column_types = 1234)

    new_rows = nrows - airlines.nrow
    if (nrows > 0):
      extra_rows = airlines[0:nrows, : ]
      airlines = airlines.rbind(extra_rows)

    airlines = airlines[0:nrows, : ]
    full_data = airlines.cbind(sample_data)

    return full_data



def xgboost_estimation():
    if ("XGBoost" not in h2o.cluster().list_all_extensions()):
        print("XGBoost extension is not present.  Skipping test. . .")
        return
     

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if not hadoop_namenode_is_accessible:
        raise EnvironmentError("Hadoop namenode is not accessible")

    hdfs_name_node = pyunit_utils.hadoop_namenode()

    full_data = createData(500000, 500)

    myX = list(full_data.col_names)
    myX.remove("IsDepDelayed")

    xgb = H2OXGBoostEstimator(seed = 42, tree_method = "approx")
    xgboost_model = xgb.train(y = "IsDepDelayed", x = myX[0:480], training_frame = full_data, model_id = "xgboost")

    print(xgboost_model)

    pred = predict(xgboost_model, full_data)
    perf = h2o.performance(xgboost_model, full_data)
    return perf


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_estimation)
else:
    xgboost_estimation()
