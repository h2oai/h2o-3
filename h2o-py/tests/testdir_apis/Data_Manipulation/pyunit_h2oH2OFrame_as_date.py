from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame
import time
import datetime
from random import randrange


def h2o_H2OFrame_as_date():
    """
    Python API test: h2o.frame.H2OFrame.as_date(format)

    Copied from pyunit_as_date.py
    """
    hdf = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/v-11-eurodate.csv"))
    temp = hdf['ds5'].as_date("%d.%m.%y %H:%M")
    assert_is_type(temp, H2OFrame)

    # choose one element from new timestamp frame and compare it with conversion by python.  Should equal.
    row_ind = randrange(0, temp.nrows)
    s = hdf[row_ind,'ds5']

    tz = h2o.cluster().timezone     # set python timezone to be the same as H2O timezone
    os.environ['TZ']=tz
    time.tzset()
    pythonTime = (time.mktime(datetime.datetime.strptime(s, "%d.%m.%y %H:%M").timetuple()))*1000.0

    assert abs(pythonTime-temp[row_ind,0]) < 1e-10, "h2o.H2OFrame.as_date() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_as_date())
else:
    h2o_H2OFrame_as_date()
