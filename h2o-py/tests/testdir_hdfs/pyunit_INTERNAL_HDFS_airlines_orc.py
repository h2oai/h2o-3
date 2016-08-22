from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils
#----------------------------------------------------------------------
# Purpose:  This test will test orc-parser in HDFS with data files of
# significant size split across multi files.  Basically, we are testing
# our multi-file parsing of Orc with big data sets.  This test is
# copied over from Nidhi R unit test.
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
                  "skipped.".format("pyunit_INTERNAL_HDFS_airlines_orc.py"))
            pass
        else:

            hdfs_orc_file = "/datasets/airlines_all_orc_parts"
            hdfs_csv_file = "/datasets/air_csv_part"

            col_types = ['real', 'real', 'real', 'real', 'real', 'real', 'real', 'real', 'enum', 'real', 'enum', 'real',
                         'real', 'enum', 'real', 'real', 'enum', 'enum', 'real', 'enum', 'enum', 'real', 'real', 'real',
                         'enum', 'enum', 'enum', 'enum', 'enum', 'enum', 'enum']

            # import CSV file
            print("Import airlines 116M dataset in original csv format from HDFS")
            url_csv = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file)

            startcsv = time.time()
            multi_file_csv = h2o.import_file(url_csv, na_strings=['\\N'], col_types=col_types)
            endcsv = time.time()

            startcsv1 = time.time()
            multi_file_csv1 = h2o.import_file(url_csv)
            endcsv1 = time.time()
            h2o.remove(multi_file_csv1)

            multi_file_csv.summary()
            csv_summary = h2o.frame(multi_file_csv.frame_id)["frames"][0]["columns"]

            # import ORC file with same column types as CSV file
            print("Import airlines 116M dataset in ORC format from HDFS")
            url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)

            startorc1 = time.time()
            multi_file_orc1 = h2o.import_file(url_orc)
            endorc1 = time.time()
            h2o.remove(multi_file_orc1)

            startorc = time.time()
            multi_file_orc = h2o.import_file(url_orc, col_types=col_types)
            endorc = time.time()

            multi_file_orc.summary()
            orc_summary = h2o.frame(multi_file_orc.frame_id)["frames"][0]["columns"]

            print("************** CSV (without column type forcing) parse time is {0}".format(endcsv1-startcsv1))
            print("************** CSV (with column type forcing) parse time is {0}".format(endcsv-startcsv))
            print("************** ORC (without column type forcing) parse time is {0}".format(endorc1-startorc1))
            print("************** ORC (with column type forcing) parse time is {0}".format(endorc-startorc))

            # compare frame read by orc by forcing column type,
            pyunit_utils.compare_frame_summary(csv_summary, orc_summary)

    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()