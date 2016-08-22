from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils
#----------------------------------------------------------------------
# test that h2o.import_file works on a directory of files!
#----------------------------------------------------------------------


def hdfs_orc_parser():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()

        tol_time = 200              # comparing in ms or ns
        tol_numeric = 1e-5          # tolerance for comparing other numeric fields
        numElements2Compare = 0   # choose number of elements per column to compare.  Save test time.

        hdfs_csv_file = "/datasets/orc_parser/synthetic_perfect_separation_csv"
        hdfs_orc_file = "/datasets/orc_parser/synthetic_perfect_separation_orc"

        url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
        url_csv = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file)


        multi_file_csv = h2o.import_file(url_csv)
        multi_file_orc = h2o.import_file(url_orc)

        # make sure orc multi-file and single big file create same H2O frame
        assert pyunit_utils.compare_frames(multi_file_orc , multi_file_csv, numElements2Compare, tol_time,
                                           tol_numeric,True), "H2O frame parsed from multiple orc and single orc " \
                                                              "files are different!"
    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()