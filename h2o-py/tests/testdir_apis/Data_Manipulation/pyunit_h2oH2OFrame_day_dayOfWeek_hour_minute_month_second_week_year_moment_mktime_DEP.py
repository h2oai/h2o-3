from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_day():
    """
    Python API test: h2o.frame.H2OFrame.day(), h2o.frame.H2OFrame.dayOfWeek(), h2o.frame.H2OFrame.hour(),
    h2o.frame.H2OFrame.minute(), h2o.frame.H2OFrame.month(), h2o.frame.H2OFrame.second(), h2o.frame.H2OFrame.week(),
    h2o.frame.H2OFrame.year(), h2o.frame.H2OFrame.moment(), h2o.frame.H2OFrame.mktime(),

    Copied from pyunit_count_temps.py
    """
    datetime = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/orc/orc2csv/TestOrcFile.testDate2038.csv"))
    datetime_day = datetime[0].day()
    checkday = datetime_day==5
    assert_is_type(datetime_day, H2OFrame)    # check return type, should be H2OFrame
    assert checkday.sum().flatten() == datetime.nrows, "h2o.H2OFrame.day() command is not working."

    datetime_dow = datetime[0].dayOfWeek()
    checkdow = datetime_dow == 'Wed'
    assert_is_type(datetime_dow, H2OFrame)    # check return type, should be H2OFrame
    assert checkdow.any(), "h2o.H2OFrame.dayOfWeek() command is not working."

    datetime_hour = datetime[0].hour()
    checkhour = datetime_hour == 12
    assert_is_type(datetime_hour, H2OFrame)    # check return type, should be H2OFrame
    assert checkhour.sum().flatten() == datetime.nrows, "h2o.H2OFrame.hour() command is not working."

    datetime_minute = datetime[0].minute()
    checkminute = datetime_minute == 34.0
    assert_is_type(datetime_minute, H2OFrame)    # check return type, should be H2OFrame
    assert checkminute.sum().flatten() == datetime.nrows, "h2o.H2OFrame.minute() command is not working."

    datetime_month = datetime[0].month()
    checkmonth = datetime_month == 5.0
    assert_is_type(datetime_month, H2OFrame)    # check return type, should be H2OFrame
    assert checkmonth.sum().flatten() == datetime.nrows, "h2o.H2OFrame.hour() command is not working."

    datetime_second = datetime[0].second()
    checksecond = datetime_second == 56.0
    assert_is_type(datetime_second, H2OFrame)    # check return type, should be H2OFrame
    assert checksecond.sum().flatten() == datetime.nrows, "h2o.H2OFrame.second() command is not working."

    datetime_week = datetime[0].week()
    checkweek = datetime_week == 18.0
    assert_is_type(datetime_week, H2OFrame)    # check return type, should be H2OFrame
    assert checkweek.any(), "h2o.H2OFrame.week() command is not working."

    datetime_year = datetime[0].year()
    checkyear= datetime_year == 2038
    assert_is_type(datetime_year, H2OFrame)    # check return type, should be H2OFrame
    assert checkyear.any(), "h2o.H2OFrame.year() command is not working."

    datetimeF=datetime[0]
    mktime_datetime = h2o.H2OFrame.moment(year=datetimeF.year(), month=datetimeF.month(), day=datetimeF.day(),
                                      hour=datetimeF.hour(), minute=datetimeF.minute(), second=datetimeF.second(),
                                      msec=0, date=None, time=None)
    assert_is_type(mktime_datetime, H2OFrame)

    datetimeF=datetime[0]
    mktime_datetime = h2o.H2OFrame.mktime(year=datetimeF.year(), month=datetimeF.month(), day=datetimeF.day(),
                                          hour=datetimeF.hour(), minute=datetimeF.minute(),
                                          second=datetimeF.second(), msec=0)
    diff = 2789999999.0
    assert_is_type(mktime_datetime, H2OFrame)
    assert abs(datetime[0,0]+diff-mktime_datetime[0,0]) < 1e-6, "h2o.H2OFrame.mktime() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_day())
else:
    h2o_H2OFrame_day()
