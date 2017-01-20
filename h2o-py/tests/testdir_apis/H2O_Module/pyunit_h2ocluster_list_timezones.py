from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2olist_timezones():
    """
    Python API test: h2o.cluster().list_timezones()
    """
    try:
        timezones = h2o.cluster().list_timezones()
        assert_is_type(timezones, H2OFrame)

        # change the assert nrow from == to >= in case more timezones are introduced in the future.
        assert timezones.nrow>=460, "h2o.cluster().list_timezones() returns frame with wrong row number."
        assert timezones.ncol==1, "h2o.cluster().list_timezones() returns frame with wrong column number."
    except Exception as e:
        assert False, "h2o.cluster().list_timezones() command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2olist_timezones)
else:
    h2olist_timezones()
