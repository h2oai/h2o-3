from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils
#----------------------------------------------------------------------
# This test will parse orc files containing timestamp and date information into
# H2O frame.  Next, it will take the .csv file generated from the orc file from
# Hive and parse into H2O frame.  Finally, we compare the two frames and make sure
# that they are equal.
#
# We want to make sure that we are parsing the date and timestamp
# date correctly from an orc file.  Thanks to Nidhi who has imported an orc file
# containing timestamp/date into spark and later into Hive and write it out as
# csv.
#
#----------------------------------------------------------------------

def hdfs_orc_parser():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()

        if pyunit_utils.cannaryHDFSTest(hdfs_name_node, "/datasets/orc_parser/orc/orc_split_elim.orc"):
            print("Your hive-exec version is too old.  Orc parser test {0} is "
                  "skipped.".format("pyunit_INTERNAL_HDFS_timestamp_date_orc.py"))
            pass
        else:
            origTZ = h2o.cluster().timezone
            newZone = 'America/Los_Angeles'
            h2o.cluster().timezone = newZone
            tol_time = 200              # comparing in ms or ns
            tol_numeric = 1e-5          # tolerance for comparing other numeric fields
            numElements2Compare = 100   # choose number of elements per column to compare.  Save test time.

            allOrcFiles = ["/datasets/orc_parser/orc/TestOrcFile.testDate1900.orc",
                           "/datasets/orc_parser/orc/TestOrcFile.testDate2038.orc",
                           "/datasets/orc_parser/orc/orc_split_elim.orc"]

            allCsvFiles = ["/datasets/orc_parser/csv/TestOrcFile.testDate1900.csv",
                           "/datasets/orc_parser/csv/TestOrcFile.testDate2038.csv",
                           "/datasets/orc_parser/csv/orc_split_elim.csv"]

            for fIndex in range(len(allOrcFiles)):
                url_orc = "hdfs://{0}{1}".format(hdfs_name_node, allOrcFiles[fIndex])
                url_csv = "hdfs://{0}{1}".format(hdfs_name_node, allCsvFiles[fIndex])
                h2oOrc = h2o.import_file(url_orc)
                h2oCsv = h2o.import_file(url_csv)

                # compare the two frames
                assert pyunit_utils.compare_frames(h2oOrc, h2oCsv, numElements2Compare, tol_time, tol_numeric), \
                    "H2O frame parsed from orc and csv files are different!"
                
            h2o.cluster().timezone=origTZ
    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()