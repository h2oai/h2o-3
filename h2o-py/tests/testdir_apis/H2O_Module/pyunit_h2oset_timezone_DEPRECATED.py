from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import random

def h2oset_timezone():
    """
    Python API test: h2o.set_timezone(value)
    Deprecated, set h2o.cluster().timezone instead.

    Copy from pyunit_get_set_list_timezones.py
    """
    origTZ = h2o.get_timezone()
    print("Original timezone: {0}".format(origTZ))

    timezones = h2o.list_timezones()
    # don't use the first one..it's a header for the table
    print("timezones[0]:", timezones[0])
    zone = timezones[random.randint(1,timezones.nrow-1),0].split(" ")[1].split(",")[0]
    print("Setting the timezone: {0}".format(zone))
    h2o.set_timezone(zone)

    newTZ = h2o.get_timezone()
    assert newTZ == zone, "Expected new timezone to be {0}, but got {01}".format(zone, newTZ)

    print("Setting the timezone back to original: {0}".format(origTZ))
    h2o.set_timezone(origTZ)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oset_timezone)
else:
    h2oset_timezone()
