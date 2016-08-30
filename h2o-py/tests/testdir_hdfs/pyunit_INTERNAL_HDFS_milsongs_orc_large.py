from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils
#----------------------------------------------------------------------
# This test will build a H2O frame from importing the bigdata/laptop/parser/orc/milsongs_orc_csv
# from and build another H2O frame from the multi-file orc parser using multiple orc files that are
# saved in the directory bigdata/laptop/parser/orc/milsongs_orc.  It will compare the two frames
# to make sure they are equal.
#----------------------------------------------------------------------


def hdfs_orc_parser():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()

        if pyunit_utils.cannaryHDFSTest(hdfs_name_node, "/datasets/orc_parser/orc/orc_split_elim.orc"):
            print("Your hive-exec version is too old.  Orc parser test {0} is "
          "skipped.".format("pyunit_INTERNAL_HDFS_milsongs_orc.py"))
            pass
        else:
            hdfs_orc_file = "/datasets/orc_parser/milsongs_orc"
            url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
            hdfs_csv_file = "/datasets/orc_parser/milsongs_csv"
            url_csv = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file)

            multi_file_csv = h2o.import_file(url_csv)
            multi_file_orc = h2o.import_file(url_orc)

            multi_file_csv.summary()
            csv_summary = h2o.frame(multi_file_csv.frame_id)["frames"][0]["columns"]

            multi_file_orc.summary()
            orc_summary = h2o.frame(multi_file_orc.frame_id)["frames"][0]["columns"]

            pyunit_utils.compare_frame_summary(csv_summary, orc_summary)
    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()