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

        if pyunit_utils.cannaryHDFSTest(hdfs_name_node, "/datasets/orc_parser/orc/orc_split_elim.orc"):
            print("Your hive-exec version is too old.  Orc parser test {0} is "
                  "skipped.".format("pyunit_INTERNAL_HDFS_import_folder_orc.py"))
            pass
        else:
            tol_time = 200              # comparing in ms or ns
            tol_numeric = 1e-5          # tolerance for comparing other numeric fields
            numElements2Compare = 0   # choose number of elements per column to compare.  Save test time.

            hdfs_csv_file1 = "/datasets/orc_parser/csv/balunbal.csv"
            url_csv1 = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file1)
            multi_file_csv1 = h2o.import_file(url_csv1)

            hdfs_csv_file2 = "/datasets/orc_parser/csv/unbalbal.csv"
            url_csv2 = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file2)
            multi_file_csv2 = h2o.import_file(url_csv2)

            hdfs_orc_file = "/datasets/orc_parser/synthetic_perfect_separation_orc"

            url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
            multi_file_orc = h2o.import_file(url_orc)

            # make sure orc multi-file and single big file create same H2O frame
            try:
                assert pyunit_utils.compare_frames(multi_file_orc , multi_file_csv1, numElements2Compare,
                                                   tol_time=tol_time, tol_numeric=tol_numeric, strict=True), \
                    "H2O frame parsed from multiple orc and single orc files are different!"
            except:
                assert pyunit_utils.compare_frames(multi_file_orc , multi_file_csv2, numElements2Compare,
                                                   tol_time=tol_time, tol_numeric=tol_numeric, strict=True), \
                    "H2O frame parsed from multiple orc and single orc files are different!"
    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()