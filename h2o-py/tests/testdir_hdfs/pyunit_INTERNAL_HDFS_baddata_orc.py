from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils
#----------------------------------------------------------------------
# This test is used to verify if the orc parser warnings from backend is
# passed down to python client when parsing orc files with unsupported
# data types or bad data value.
#----------------------------------------------------------------------

def hdfs_orc_parser():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()

        hdfs_orc_file = "/datasets/orc_parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc"
        url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
        assert pyunit_utils.expect_warnings(url_orc, "UserWarning:", "Skipping field:", 1),\
            "Expect warnings from orc parser for file "+url_orc+"!"

        hdfs_orc_file = "/datasets/orc_parser/orc/TestOrcFile.emptyFile.orc"
        url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
        assert pyunit_utils.expect_warnings(url_orc, "UserWarning:", "Skipping field:", 1), \
            "Expect warnings from orc parser for file "+url_orc+"!"

        hdfs_orc_file = "/datasets/orc_parser/orc/nulls-at-end-snappy.orc"
        url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
        assert pyunit_utils.expect_warnings(url_orc, "UserWarning:", "Skipping field:", 1), \
            "Expect warnings from orc parser for file "+url_orc+"!"

    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()