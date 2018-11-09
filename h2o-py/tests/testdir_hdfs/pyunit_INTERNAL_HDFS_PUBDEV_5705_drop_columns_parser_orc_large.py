from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import random

def import_folder_orc():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:

        hdfs_name_node = pyunit_utils.hadoop_namenode()

        if pyunit_utils.cannaryHDFSTest(hdfs_name_node, "/datasets/orc_parser/orc/orc_split_elim.orc"):
            print("Your hive-exec version is too old.  Orc parser test {0} is "
                  "skipped.".format("pyunit_INTERNAL_HDFS_airlines_orc.py"))
            pass
        else:

            hdfs_orc_file = "/datasets/orc_parser/prostate_NA.orc"
            hdfs_csv_file = "/datasets/orc_parser/prostate_NA.csv"

    url_csv = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file)
    url_orc = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_orc_file)
    csv = h2o.import_file(url_csv, na_strings=['\\N'])
    multi_file_orc1 = h2o.import_file(url_orc)
    pyunit_utils.compare_frames_local(csv, multi_file_orc1, prob=1)  # should be the same here.

    path = url_orc
    skip_all = list(range(csv.ncol))
    skip_even = list(range(0, csv.ncol, 2))
    skip_odd = list(range(1, csv.ncol, 2))
    skip_start_end = [0, csv.ncol - 1]
    skip_except_last = list(range(0, csv.ncol - 2))
    skip_except_first = list(range(1, csv.ncol))
    temp = list(range(0, csv.ncol))
    random.shuffle(temp)
    skip_random = []
    for index in range(0, csv.ncol / 2):
        skip_random.append(temp[index])
    skip_random.sort()

    try:
        loadFileSkipAll = h2o.upload_file(path, skipped_columns=skip_all)
        sys.exit(1)  # should have failed here
    except:
        pass

    try:
        importFileSkipAll = h2o.import_file(path, skipped_columns=skip_all)
        sys.exit(1)  # should have failed here
    except:
        pass

    # skip even columns
    pyunit_utils.checkCorrectSkips(csv, path, skip_even)

    # skip odd columns
    pyunit_utils.checkCorrectSkips(csv, path, skip_odd)

    # skip the very beginning and the very end.
    pyunit_utils.checkCorrectSkips(csv, path, skip_start_end)

    # skip all except the last column
    pyunit_utils.checkCorrectSkips(csv, path, skip_except_last)

    # skip all except the very first column
    pyunit_utils.checkCorrectSkips(csv, path, skip_except_first)

    # randomly skipped half the columns
    pyunit_utils.checkCorrectSkips(csv, path, skip_random)


if __name__ == "__main__":
    pyunit_utils.standalone_test(import_folder_orc)
else:
    import_folder_orc()
