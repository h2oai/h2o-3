import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
################################################################################
##
## Verifying that a user can change a column type to Enum if they like.
##
################################################################################


def continuous_or_categorical_orc():
    numElements2Compare = 100
    tol_time = 200
    tol_numeric = 1e-5

    h2oframe_csv = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    data_types = ['real', 'real', 'real', 'real', 'enum']
    h2oframe_orc = h2o.import_file(pyunit_utils.locate("smalldata/parser/orc/iris.orc"), col_types = data_types)

    # compare the two frames
    assert pyunit_utils.compare_frames(h2oframe_orc, h2oframe_csv, numElements2Compare, tol_time, tol_numeric, True), \
        "H2O frame parsed from orc and csv files are different!"



if __name__ == "__main__":
    pyunit_utils.standalone_test(continuous_or_categorical_orc)
else:
    continuous_or_categorical_orc()
