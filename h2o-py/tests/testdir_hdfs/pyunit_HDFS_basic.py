#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from python.
#----------------------------------------------------------------------

import sys
sys.path.insert(1, "../../")
import h2o, tests

def hdfs_basic(ip, port):
    

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    running_inside_h2o = tests.is_running_internal_to_h2o()

    if running_inside_h2o:
        hdfs_name_node = tests.get_h2o_internal_hdfs_name_node()
        hdfs_iris_file = "/datasets/runit/iris_wheader.csv"
        hdfs_iris_dir  = "/datasets/runit/iris_test_train"

        #----------------------------------------------------------------------
        # Single file cases.
        #----------------------------------------------------------------------

        print "Testing single file importHDFS"
        url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_iris_file)
        iris_h2o = h2o.import_file(url)
        iris_h2o.head()
        iris_h2o.tail()
        n = iris_h2o.nrow
        print "rows: {0}".format(n)
        assert n == 150, "Wrong number of rows. Got {0}. Should have got {1}".format(n, 150)
        assert isinstance(iris_h2o, h2o.H2OFrame), "Wrong type. Expected H2OFrame, but got {0}".format(type(iris_h2o))
        print "Import worked"

        #----------------------------------------------------------------------
        # Directory file cases.
        #----------------------------------------------------------------------

        print "Testing directory importHDFS"
        urls = ["hdfs://{0}{1}/iris_test.csv".format(hdfs_name_node, hdfs_iris_dir),
                "hdfs://{0}{1}/iris_train.csv".format(hdfs_name_node, hdfs_iris_dir)]
        iris_dir_h2o = h2o.import_file(urls)
        iris_dir_h2o.head()
        iris_dir_h2o.tail()
        n = iris_dir_h2o.nrow
        print "rows: {0}".format(n)
        assert n == 150, "Wrong number of rows. Got {0}. Should have got {1}".format(n, 150)
        assert isinstance(iris_dir_h2o, h2o.H2OFrame), "Wrong type. Expected H2OFrame, but got {0}".\
            format(type(iris_dir_h2o))
        print "Import worked"
    else:
        print "Not running on H2O internal network.  No access to HDFS."

if __name__ == "__main__":
    tests.run_test(sys.argv, hdfs_basic)
