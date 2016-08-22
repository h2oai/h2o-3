from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
#----------------------------------------------------------------------
# Purpose:  This test will test orc-parser in HDFS parsing multiple
#           orc files collected by Tom K.
#----------------------------------------------------------------------


def hdfs_orc_parser():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        numElements2Compare = 10
        tol_time = 200
        tol_numeric = 1e-5

        hdfs_name_node = pyunit_utils.hadoop_namenode()

        if pyunit_utils.cannaryHDFSTest(hdfs_name_node, "/datasets/orc_parser/orc/orc_split_elim.orc"):
            print("Your hive-exec version is too old.  Orc parser test {0} is "
                  "skipped.".format("pyunit_INTERNAL_HDFS_orc_parser.py"))
            pass
        else:

            allOrcFiles = ["/datasets/orc_parser/orc/TestOrcFile.columnProjection.orc",
                           "/datasets/orc_parser/orc/bigint_single_col.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.emptyFile.orc",
                           "/datasets/orc_parser/orc/bool_single_col.orc",
                           "/datasets/orc_parser/orc/demo-11-zlib.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testDate1900.orc",
                           "/datasets/orc_parser/orc/demo-12-zlib.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testDate2038.orc",
                           "/datasets/orc_parser/orc/double_single_col.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testMemoryManagementV11.orc",
                           "/datasets/orc_parser/orc/float_single_col.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testMemoryManagementV12.orc",
                           "/datasets/orc_parser/orc/int_single_col.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testPredicatePushdown.orc",
                           "/datasets/orc_parser/orc/nulls-at-end-snappy.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testSnappy.orc",
                           "/datasets/orc_parser/orc/orc_split_elim.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testStripeLevelStats.orc",
                           "/datasets/orc_parser/orc/smallint_single_col.orc",
                           "/datasets/orc_parser/orc/string_single_col.orc",
                           "/datasets/orc_parser/orc/tinyint_single_col.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testWithoutIndex.orc"]


            for fIndex in range(len(allOrcFiles)):
                url_orc = "hdfs://{0}{1}".format(hdfs_name_node, allOrcFiles[fIndex])
                tab_test = h2o.import_file(url_orc)
    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()