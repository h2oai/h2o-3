import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def refine_date_col(data, col, pattern):
  data[col]         = data[col].as_date(pattern)
  data["Day"]       = data[col].day()
  data["Month"]     = data[col].month() + 1    # Since H2O indexes from 0
  data["Year"]      = data[col].year() + 1900  # Start of epoch is 1900
  data["WeekNum"]   = data[col].week()
  data["WeekDay"]   = data[col].dayOfWeek()
  data["HourOfDay"] = data[col].hour()

  # Create weekend and season cols
  # Spring = Mar, Apr, May. Summer = Jun, Jul, Aug. Autumn = Sep, Oct. Winter = Nov, Dec, Jan, Feb.
  # data["Weekend"] = [1 if x in ("Sun", "Sat") else 0 for x in data["WeekDay"]]
  data["Weekend"] = (data["WeekDay"] == "Sun") | (data["WeekDay"] == "Sat")
  assert data["Weekend"].min() < data["Weekend"].max() # Not a constant result
  data["Season"]  = data["Month"].cut([0, 2, 5, 7, 10, 12], ["Winter", "Spring", "Summer", "Autumn", "Winter"])


def date_munge():
  crimes_path = pyunit_utils.locate("smalldata/chicago/chicagoCrimes10k.csv.zip")
  crimes = h2o.import_file(path=crimes_path)
  crimes.describe()

  refine_date_col(crimes, "Date", "%m/%d/%Y %I:%M:%S %p")
  crimes = crimes.drop("Date")
  crimes.describe()



if __name__ == "__main__":
    pyunit_utils.standalone_test(date_munge)
else:
    date_munge()
