from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils


def import_folder():
    """
    This test will build a H2O frame from importing the bigdata/laptop/parser/orc/airlines_05p_orc_csv
    from and build another H2O frame from the multi-file orc parser using multiple orc files that are
    saved in the directory bigdata/laptop/parser/orc/airlines_05p_orc.  It will compare the two frames
    to make sure they are equal.
    :return: None if passed.  Otherwise, an exception will be thrown.
    """
    startcsv = time.time()
    multi_file_csv = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/parser/orc/pubdev_3200/air05_csv"),
                                     na_strings=['\\N'])
    endcsv = time.time()

    csv_type_dict = multi_file_csv.types

    multi_file_csv.summary()
    csv_summary = h2o.frame(multi_file_csv.frame_id)["frames"][0]["columns"]

    col_ind_name = dict()
    # change column types from real to enum according to multi_file_csv column types
    for key_name in list(csv_type_dict):
        col_ind = key_name.split('C')
        new_ind = int(str(col_ind[1]))-1
        col_ind_name[new_ind] = key_name

    col_types = []
    for ind in range(len(col_ind_name)):
        col_types.append(csv_type_dict[col_ind_name[ind]])

    startorc1 = time.time()
    multi_file_orc1 = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/parser/orc/pubdev_3200/air05_orc"))
    endorc1 = time.time()
    h2o.remove(multi_file_orc1)

    startorc = time.time()
    multi_file_orc = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/parser/orc/pubdev_3200/air05_orc"),
                                     col_types=col_types)
    endorc = time.time()

    multi_file_orc.summary()
    orc_summary = h2o.frame(multi_file_orc.frame_id)["frames"][0]["columns"]

    print("************** CSV parse time is {0}".format(endcsv-startcsv))
    print("************** ORC (without column type forcing) parse time is {0}".format(endorc1-startorc1))
    print("************** ORC (with column type forcing) parse time is {0}".format(endorc-startorc))
    # compare frame read by orc by forcing column type,
    pyunit_utils.compare_frame_summary(csv_summary, orc_summary)


if __name__ == "__main__":
    pyunit_utils.standalone_test(import_folder)
else:
    import_folder()
