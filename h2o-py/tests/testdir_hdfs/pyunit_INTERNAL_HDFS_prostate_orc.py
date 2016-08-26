from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils
#----------------------------------------------------------------------
# To verify that the orc parser is parsing correctly, we want to take a file we know (prostate_NA.csv), convert
# it to an Orc file (prostate_NA.orc) and build two H2O frames out of them.   We compare them and verified that
# they are the same.
#
# Nidhi did this manually in Hive and verified that the parsing is correct.  I am automating the test here.
#
#----------------------------------------------------------------------


def hdfs_orc_parser():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()

        # run a quick test to determine if the hive-exec is too old.

        if pyunit_utils.cannaryHDFSTest(hdfs_name_node, "/datasets/orc_parser/orc/orc_split_elim.orc"):
            print("Your hive-exec version is too old.  Orc parser test {0} is "
                  "skipped.".format("pyunit_INTERNAL_HDFS_prostate_orc.py"))
            pass
        else:

            tol_time = 200              # comparing in ms or ns
            tol_numeric = 1e-5          # tolerance for comparing other numeric fields
            numElements2Compare = 10   # choose number of elements per column to compare.  Save test time.

            hdfs_orc_file = "/datasets/orc_parser/orc/prostate_NA.orc"
            hdfs_csv_file = "/datasets/orc_parser/csv/prostate_NA.csv"
            url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
            url_csv = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file)

            h2oOrc = h2o.import_file(url_orc)
            h2oCsv = h2o.import_file(url_csv)

            # compare the two frames
            assert pyunit_utils.compare_frames(h2oOrc, h2oCsv, numElements2Compare, tol_time, tol_numeric), \
                "H2O frame parsed from orc and csv files are different!"
    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()