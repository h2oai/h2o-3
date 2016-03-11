from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import os
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def pubdev_1480():

    if not pyunit_utils.hadoop_namenode_is_accessible(): raise EnvironmentError
    train = h2o.import_file("hdfs://mr-0xd6/datasets/kaggle/sf.crime.train.gz")
    test = h2o.import_file("hdfs://mr-0xd6/datasets/kaggle/sf.crime.test.gz")

    model=H2OGradientBoostingEstimator()
    model.train(x=list(range(2,9)),y=1,training_frame=train)

    predictions = model.predict(test)

    results_dir = pyunit_utils.locate("results")
    h2o.download_csv(predictions, os.path.join(results_dir,"predictions.csv"))



if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_1480)
else:
    pubdev_1480()
