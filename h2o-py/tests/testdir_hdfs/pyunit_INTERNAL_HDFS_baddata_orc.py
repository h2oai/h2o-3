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

        if pyunit_utils.cannaryHDFSTest(hdfs_name_node, "/datasets/orc_parser/orc/orc_split_elim.orc"):
            print("Your hive-exec version is too old.  Orc parser test {0} is "
                  "skipped.".format("pyunit_INTERNAL_HDFS_baddata_orc.py"))
            pass
        else:
            hdfs_orc_file = "/datasets/orc_parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc"
            url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
            print("Parsing the orc file {0}".format(url_orc))
            assert pyunit_utils.expect_warnings(url_orc, warn_phrase="UserWarning:",
                                                warn_string_of_interest="Skipping field:", in_hdfs=True,
                                                number_of_times=1), "Expect warnings from orc parser for file "+url_orc+"!"

            hdfs_orc_file = "/datasets/orc_parser/orc/TestOrcFile.emptyFile.orc"
            url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
            print("Parsing the orc file {0}".format(url_orc))
            assert pyunit_utils.expect_warnings(url_orc, warn_phrase="UserWarning:",
                                                warn_string_of_interest="Skipping field:", in_hdfs=True,
                                                number_of_times=1), "Expect warnings from orc parser for file "+url_orc+"!"

            hdfs_orc_file = "/datasets/orc_parser/orc/nulls-at-end-snappy.orc"
            url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
            print("Parsing the orc file {0}".format(url_orc))
            assert pyunit_utils.expect_warnings(url_orc, warn_phrase="UserWarning:",
                                                warn_string_of_interest="Long.MIN_VALUE:", in_hdfs=True,
                                                number_of_times=1), "Expect warnings from orc parser for file "+url_orc+"!"

    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()