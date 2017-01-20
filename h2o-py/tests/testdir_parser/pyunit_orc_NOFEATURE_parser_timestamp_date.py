from builtins import str
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def orc_parser_timestamp_date():
    """
    This test will parse orc files containing timestamp and date information into
    H2O frame.  Next, it will take the .csv file generated from the orc file from
    Hive and parse into H2O frame.  Finally, we compare the two frames and make sure
    that they are equal.

    We want to make sure that we are parsing the date and timestamp
    date correctly from an orc file.  Thanks to Nidhi who has imported an orc file
    containing timestamp/date into spark and later into Hive and write it out as
    csv.

    :return: None
    """
    origTZ = h2o.cluster().timezone
    newZone = 'America/Los_Angeles'
    h2o.cluster().timezone = newZone

    tol_time = 200              # comparing in ms or ns
    tol_numeric = 1e-5          # tolerance for comparing other numeric fields
    numElements2Compare = 100   # choose number of elements per column to compare.  Save test time.

    allOrcFiles = ["smalldata/parser/orc/TestOrcFile.testDate1900.orc",
                   "smalldata/parser/orc/TestOrcFile.testDate2038.orc",
                   "smalldata/parser/orc/orc_split_elim.orc"]

    allCsvFiles = ["smalldata/parser/orc/orc2csv/TestOrcFile.testDate1900.csv",
                   "smalldata/parser/orc/orc2csv/TestOrcFile.testDate2038.csv",
                   "smalldata/parser/orc/orc2csv/orc_split_elim.csv"]

    for fIndex in range(len(allOrcFiles)):

        h2oOrc = h2o.import_file(path=pyunit_utils.locate(allOrcFiles[fIndex]))
        h2oCsv = h2o.import_file(path=pyunit_utils.locate(allCsvFiles[fIndex]))

        # compare the two frames
        assert pyunit_utils.compare_frames(h2oOrc, h2oCsv, numElements2Compare, tol_time, tol_numeric), \
            "H2O frame parsed from orc and csv files are different!"

    h2o.cluster().timezone=origTZ
if __name__ == "__main__":
    pyunit_utils.standalone_test(orc_parser_timestamp_date)
else:
    orc_parser_timestamp_date()

