from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame


def h2o_H2OFrame_countmatches():
    """
    Python API test: h2o.frame.H2OFrame.countmatches(pattern)

    Copied from pyunit_countmatches.py
    """
    python_lists = [["what","is"], ["going", "on"], ["When", "are"], ["MeetingMeetingon", "gone"]]
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    matches = h2oframe.countmatches(['Wh', 'ing', 'on'])
    assert_is_type(matches, H2OFrame)
    assert matches.shape == h2oframe.shape, "h2o.H2OFrame.countmatches() command is not working."
    assert matches.any_na_rm(), "h2o.H2OFrame.countmatches() command is not working."
    nomatches = h2oframe.countmatches(['rain','pluck'])
    assert not(nomatches.any_na_rm()), "h2o.H2OFrame.countmatches() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_countmatches())
else:
    h2o_H2OFrame_countmatches()
