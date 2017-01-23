from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils


def date_munge():
    crimes_path = pyunit_utils.locate("smalldata/chicago/chicagoCrimes10k.csv.zip")
    # crimes_path = "smalldata/chicago/chicagoCrimes10k.csv.zip"

    hc = h2o.connection()
    assert hc.session_id  # Make sure the `POST /4/session` call has happened
    tmps0 = pyunit_utils.temp_ctr()

    # GET /3/ImportFiles
    # POST /3/ParseSetup
    # POST /3/Parse
    # GET /3/Job/{job_id}  (multiple times)
    # GET /3/Frames/crimes
    crimes = h2o.import_file(path=crimes_path, destination_frame="crimes")

    rest1 = hc.requests_count

    crimes["Day"] = crimes["Date"].day()
    crimes["Month"] = crimes["Date"].month() + 1    # Since H2O indexes from 0
    crimes["Year"] = crimes["Date"].year() + 1900  # Start of epoch is 1900
    crimes["WeekNum"] = crimes["Date"].week()
    crimes["WeekDay"] = crimes["Date"].dayOfWeek()
    crimes["HourOfDay"] = crimes["Date"].hour()
    print("# of REST calls used: %d" % (hc.requests_count - rest1))

    crimes["Weekend"] = (crimes["WeekDay"] == "Sun") | (crimes["WeekDay"] == "Sat")
    print("# of REST calls used: %d" % (hc.requests_count - rest1))

    crimes["Season"] = crimes["Month"].cut([0, 2, 5, 7, 10, 12], ["Winter", "Spring", "Summer", "Autumn", "Winter"])
    print("# of REST calls used: %d" % (hc.requests_count - rest1))

    crimes = crimes.drop("Date")
    print("# of REST calls used: %d" % (hc.requests_count - rest1))

    # POST /99/Rapids  {ast:(tmp= py8 (cols (append
    #                        (tmp= py7 (append
    #                         (tmp= py6 (append
    #                          (tmp= py5 (append
    #                           (tmp= py4 (append
    #                            (tmp= py3 (:=
    #                             (tmp= py2 (append
    #                              (tmp= py1 (append crimes (day (cols_py chicagoCrimes10k.hex "Date")) "Day")
    #                               ) (+ (month (cols_py py1 "Date")) 1) "Month"))
    #                                 (+ (year (cols_py py2 "Date")) 1900) 17 []))
    #                                    (week (cols_py py3 "Date")) "WeekNum"))
    #                                    (dayOfWeek (cols_py py4 "Date")) "WeekDay"))
    #                                    (hour (cols_py py5 "Date")) "HourOfDay"))
    #                                 (| (== (cols_py py6 "WeekDay") "Sun")
    #                                    (== (cols_py py6 "WeekDay") "Sat")) "Weekend"))
    #                         (cut (cols_py py7 "Month") [0 2 5 7 10 12]
    #                           ["Winter" "Spring" "Summer" "Autumn" "Winter"] FALSE TRUE 3) "Season") -3))}
    # GET /3/Frames/py8
    crimes.describe()
    print("# of REST calls used: %d" % (hc.requests_count - rest1))

    ntmps = pyunit_utils.temp_ctr() - tmps0
    nrest = pyunit_utils.rest_ctr() - rest1
    print("Number of temps used: %d" % ntmps)
    print("Number of RESTs used: %d" % nrest)
    assert ntmps == 8
    assert nrest == 2



if __name__ == "__main__":
    pyunit_utils.standalone_test(date_munge)
else:
    date_munge()
