import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils

def orc_parser_baddata():
    """
    This test is used to verify if the orc parser warnings from backend is passed down to python client
    when parsing orc files with unsupported data types or bad data value.

    :return: None or a fit if no warning is captured
    """
    fileWithPath = "smalldata/parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc"
    assert pyunit_utils.expect_warnings(fileWithPath, "UserWarning:", "Skipping field:", 1), \
        "Expect warnings from orc parser for file "+fileWithPath+"!"

    fileWithPath = "smalldata/parser/orc/TestOrcFile.emptyFile.orc"
    assert pyunit_utils.expect_warnings(fileWithPath, "UserWarning:", "Skipping field:", 4), \
        "Expect warnings from orc parser for file "+fileWithPath+"!"

    fileWithPath = "smalldata/parser/orc/nulls-at-end-snappy.orc"
    assert pyunit_utils.expect_warnings(fileWithPath, "UserWarning:", "Long.MIN_VALUE:", 1), \
        "Expect warnings from orc parser for file "+fileWithPath+"!"

if __name__ == "__main__":
    pyunit_utils.standalone_test(orc_parser_baddata)
else:
    orc_parser_baddata()
