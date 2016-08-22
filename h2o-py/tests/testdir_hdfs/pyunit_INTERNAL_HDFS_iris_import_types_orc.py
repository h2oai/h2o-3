from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils
#----------------------------------------------------------------------
## Verifying that a user can change a column type to Enum if they like.
#----------------------------------------------------------------------


def hdfs_orc_parser():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()

        if pyunit_utils.cannaryHDFSTest(hdfs_name_node, "/datasets/orc_parser/orc/orc_split_elim.orc"):
            print("Your hive-exec version is too old.  Orc parser test {0} is "
                  "skipped.".format("pyunit_INTERNAL_HDFS_iris_import_types_orc.py"))
            pass
        else:

            numElements2Compare = 100
            tol_time = 200
            tol_numeric = 1e-5

            hdfs_orc_file = "/datasets/orc_parser/orc/iris.orc"
            url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
            hdfs_csv_file = "/datasets/orc_parser/csv/iris.csv"
            url_csv = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file)

            h2oframe_csv = h2o.import_file(url_csv)
            data_types = ['real', 'real', 'real', 'real', 'enum']
            h2oframe_orc = h2o.import_file(url_orc, col_types = data_types)

            # compare the two frames
            assert pyunit_utils.compare_frames(h2oframe_orc, h2oframe_csv, numElements2Compare, tol_time, tol_numeric,
                                               True), "H2O frame parsed from orc and csv files are different!"
    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()