import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
################################################################################
##
## Verifying that Python can define features as categorical or continuous on import
##
################################################################################


def continuous_or_categorical():
    numElements2Compare = 0
    tol_time = 200
    tol_numeric = 1e-5

    ctypes = ["enum"]*3
    h2oframe_csv = h2o.import_file(pyunit_utils.locate("smalldata/jira/hexdev_29.csv"), col_types=ctypes)
    h2oframe_orc = h2o.import_file(pyunit_utils.locate("smalldata/parser/orc/hexdev_29.orc"), col_types=ctypes)

    # compare the two frames
    assert pyunit_utils.compare_frames(h2oframe_orc, h2oframe_csv, numElements2Compare, tol_time, tol_numeric, True), \
        "H2O frame parsed from orc and csv files are different!"



if __name__ == "__main__":
    pyunit_utils.standalone_test(continuous_or_categorical)
else:
    continuous_or_categorical()
