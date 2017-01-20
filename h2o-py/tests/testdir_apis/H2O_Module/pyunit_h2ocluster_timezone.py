from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2oget_timezone():
    """
    Python API test: h2o.cluster().timezone() to get the time zone, h2o.cluster().timezone="UTC" to
    test setting the time zone.

    Copy from pyunit_get_set_list_timezones.py
    """
    try:
        origTZ = h2o.cluster().timezone
        print("Original timezone: {0}".format(origTZ))

        newZone = 'America/Los_Angeles'
        h2o.cluster().timezone = newZone
        assert str(h2o.cluster().timezone)==newZone, "Time zone was not set correctly."
        h2o.cluster().timezone=origTZ   # reset timezone back to original one
    except Exception as e:
        assert False, "h2o.cluster().timezone command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oget_timezone)
else:
    h2oget_timezone()
