import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def date_munge():
  crimes_path = pyunit_utils.locate("smalldata/chicago/chicagoCrimes10k.csv.zip")
  # crimes_path = "smalldata/chicago/chicagoCrimes10k.csv.zip"

  tmps0 = pyunit_utils.temp_ctr() # Expected 0
  rest0 = pyunit_utils.rest_ctr() # Expected 0

  # /3/ImportFiles
  # /3/ParseSetup
  # /3/Parse
  crimes = h2o.import_file(path=crimes_path)

  # /3/Frames/chicagoCrimes10k.hex - head 10 rows, basic stats
  # /99/Rapids, parms: {ast=(tmp= py_1 (:= chicagoCrimes10k.hex (as.Date (cols_py chicagoCrimes10k.hex "Date") "%m/%d/%Y %I:%M:%S %p") 2 []))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=chicagoCrimes10k.hex}
  # /3/Frames/py_1, route: /3/Frames/(?<frameid>.*), parms: {frame_id=py_1, row_count=10}
  crimes["Date"]      = crimes["Date"].as_date("%m/%d/%Y %I:%M:%S %p")

  # /99/Rapids, parms: {ast=(tmp= py_2 (append py_1 (day (cols_py py_1 "Date")) "Day"))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=py_1}
  # /3/Frames/(?<frameid>.*), parms: {frame_id=py_2, row_count=10}
  crimes["Day"]       = crimes["Date"].day()

  # /99/Rapids, parms: {ast=(tmp= py_3 (append py_2 (+ (month (cols_py py_2 "Date")) 1) "Month"))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=py_2}
  # /3/Frames/(?<frameid>.*), parms: {frame_id=py_3, row_count=10}
  # /99/Rapids, parms: {ast=(tmp= py_4 (:= py_3 (+ (year (cols_py py_3 "Date")) 1900) 17 []))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=py_3}
  # /3/Frames/(?<frameid>.*), parms: {frame_id=py_4, row_count=10}
  # /99/Rapids, parms: {ast=(tmp= py_5 (append py_4 (week (cols_py py_4 "Date")) "WeekNum"))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=py_4}
  # /3/Frames/(?<frameid>.*), parms: {frame_id=py_5, row_count=10}
  # /99/Rapids, parms: {ast=(tmp= py_6 (append py_5 (dayOfWeek (cols_py py_5 "Date")) "WeekDay"))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=py_5}
  # /3/Frames/py_6, route: {frame_id=py_6, row_count=10}
  # /99/Rapids(append py_6 (hour (cols_py py_6 "Date")) "HourOfDay"))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=py_6}
  # /3/Frames/(?<frameid>.*), parms: {frame_id=py_7, row_count=10}
  crimes["Month"]     = crimes["Date"].month() + 1    # Since H2O indexes from 0
  crimes["Year"]      = crimes["Date"].year() + 1900  # Start of epoch is 1900
  crimes["WeekNum"]   = crimes["Date"].week()
  crimes["WeekDay"]   = crimes["Date"].dayOfWeek()
  crimes["HourOfDay"] = crimes["Date"].hour()

  # /99/Rapids, parms: {ast=(tmp= py_8 (append py_7 (| (== (cols_py py_7 "WeekDay") "Sun") (== (cols_py py_7 "WeekDay") "Sat")) "Weekend"))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=py_7}
  # /3/Frames/(?<frameid>.*), parms: {frame_id=py_8, row_count=10}
  crimes["Weekend"] = (crimes["WeekDay"] == "Sun") | (crimes["WeekDay"] == "Sat")

  # /99/Rapids, parms: {ast=(tmp= py_9 (append py_8 (cut (cols_py py_8 "Month") [0 2 5 7 10 12] ["Winter" "Spring" "Summer" "Autumn" "Winter"] FALSE TRUE 3) "Season"))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=py_8}
  # /3/Frames/(?<frameid>.*), parms: {frame_id=py_9, row_count=10}
  crimes["Season"]  = crimes["Month"].cut([0, 2, 5, 7, 10, 12], ["Winter", "Spring", "Summer", "Autumn", "Winter"])

  # /99/Rapids, parms: {ast=(tmp= py_10 (cols py_9 -3))}
  # DELETE /3/DKV/(?<key>.*), parms: {key=py_9}
  # /3/Frames/(?<frameid>.*), parms: {frame_id=py_10, row_count=10}
  crimes = crimes.drop("Date")

  crimes.describe()

  # DELETE /3/DKV/(?<key>.*), parms: {key=py_10}

  tmps1 = pyunit_utils.temp_ctr(); ntmps = tmps1-tmps0
  rest1 = pyunit_utils.rest_ctr(); nrest = rest1-rest0
  print("Number of temps used: ",ntmps)
  print("Number of RESTs used: ",nrest)
  assert ntmps <= 10
  assert nrest < 30

if __name__ == "__main__":
  pyunit_utils.standalone_test(date_munge)
else:
  date_munge()

