import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import os


def pubdev_1480():

    if not pyunit_utils.hadoop_namenode_is_accessible(): raise(EnvironmentError, "Not running on H2O internal network.  No access to HDFS.")
    train = h2o.import_file("hdfs://mr-0xd6/datasets/kaggle/sf.crime.train.gz")
    test = h2o.import_file("hdfs://mr-0xd6/datasets/kaggle/sf.crime.test.gz")

    model = h2o.gbm(x=train[range(2,9)], y=train[1])

    predictions = model.predict(test)

    results_dir = pyunit_utils.locate("results")
    h2o.download_csv(predictions, os.path.join(results_dir,"predictions.csv"))



if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_1480)
else:
    pubdev_1480()
