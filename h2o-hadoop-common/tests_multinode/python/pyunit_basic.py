import sys
import os
sys.path.insert(1, os.path.join("..", "..", "..", "h2o-py"))
from tests import pyunit_utils
import h2o

# Purpose:  This test exercises HDFS operations from python.

def hdfs_basic():
    hdfs_name_node = pyunit_utils.hadoop_namenode()
    hdfs_iris_file = "/datasets/runit/iris_wheader.csv"
    hdfs_iris_dir  = "/datasets/runit/iris_test_train"

    print("Testing single file importHDFS")
    url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_iris_file)
    iris_h2o = h2o.import_file(url)
    n = iris_h2o.nrow
    print("rows: {0}".format(n))
    assert n == 150, "Wrong number of rows. Got {0}. Should have got {1}".format(n, 150)
    assert isinstance(iris_h2o, h2o.H2OFrame), "Wrong type. Expected H2OFrame, but got {0}".format(type(iris_h2o))
    print("Import worked")

    print("Testing directory importHDFS")
    urls = ["hdfs://{0}{1}/iris_test.csv".format(hdfs_name_node, hdfs_iris_dir),
            "hdfs://{0}{1}/iris_train.csv".format(hdfs_name_node, hdfs_iris_dir)]
    iris_dir_h2o = h2o.import_file(urls)
    iris_dir_h2o.head()
    iris_dir_h2o.tail()
    n = iris_dir_h2o.nrow
    print("rows: {0}".format(n))
    assert n == 150, "Wrong number of rows. Got {0}. Should have got {1}".format(n, 150)
    assert isinstance(iris_dir_h2o, h2o.H2OFrame), "Wrong type. Expected H2OFrame, but got {0}".\
        format(type(iris_dir_h2o))
    print("Import worked")


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_basic)
else:
    hdfs_basic()
