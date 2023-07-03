import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import urllib.request, urllib.parse, urllib.error

################################################################################
#
# Verifying that we can use na_omit() to remove rows containing nas from a
# H2O frame.
#
################################################################################


def test_na_omits():
    hf = h2o.H2OFrame({'A': [1, 'NA', 2], 'B': [1, 2, 3], 'C': [4, 5, 6]})
    hf.summary()
    hf_col_summary = h2o.api("GET /3/Frames/%s/summary" % urllib.parse.quote(hf.frame_id))["frames"][0]["columns"]
    hf_col_summary = sum([e["missing_count"] for e in hf_col_summary])
    assert hf_col_summary == 1  # we have one NAN here

    # attempt to remove it by calling na_omit
    hf_naomit = hf.na_omit()
    hf_naomit.summary()
    hf_naomit_col_summary = \
        h2o.api("GET /3/Frames/%s/summary" % urllib.parse.quote(hf_naomit.frame_id))["frames"][0]["columns"]
    hf_naomit_col_summary = sum([e["missing_count"] for e in hf_naomit_col_summary])
    assert hf_naomit_col_summary == 0  # we have removed the NAN row

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_na_omits)
else:
    test_na_omits()
