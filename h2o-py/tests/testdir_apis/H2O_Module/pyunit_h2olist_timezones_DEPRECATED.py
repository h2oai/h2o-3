import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame


def h2olist_timezones():
    """
    Python API test: h2o.list_timezones()
    Deprecated, use h2o.cluster().list_timezones().
    """
    timezones = h2o.list_timezones()
    assert_is_type(timezones, H2OFrame)

    assert timezones.nrow == 459, "h2o.get_timezone() returns frame with wrong row number."
    assert timezones.ncol == 1, "h2o.get_timezone() returns frame with wrong column number."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2olist_timezones)
else:
    h2olist_timezones()
