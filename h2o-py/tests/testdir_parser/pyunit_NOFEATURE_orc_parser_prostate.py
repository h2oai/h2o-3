from builtins import str
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def orc_parser_timestamp_date():
    """
    To verify that the orc parser is parsing correctly, we want to take a file we know (prostate_NA.csv), convert
    it to an Orc file (prostate_NA.orc) and build two H2O frames out of them.   We compare them and verified that
    they are the same.

    Nidhi did this manually in Hive and verified that the parsing is correct.  I am automating the test here.

    :return: None
    """

    tol_time = 200              # comparing in ms or ns
    tol_numeric = 1e-5          # tolerance for comparing other numeric fields
    numElements2Compare = 10   # choose number of elements per column to compare.  Save test time.

    h2oOrc = h2o.import_file(path=pyunit_utils.locate('smalldata/parser/orc/prostate_NA.orc'))
    h2oCsv = h2o.import_file(path=pyunit_utils.locate('smalldata/parser/csv2orc/prostate_NA.csv'))

    # compare the two frames
    assert pyunit_utils.compare_frames(h2oOrc, h2oCsv, numElements2Compare, tol_time, tol_numeric), \
        "H2O frame parsed from orc and csv files are different!"


if __name__ == "__main__":
    pyunit_utils.standalone_test(orc_parser_timestamp_date)
else:
    orc_parser_timestamp_date()
