import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def test_as_data():
  hdf = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/v-11-eurodate.csv"))
  print(hdf.head())

  # NB: columns 1,5 are currently unsupported as date types
  # that is, h2o cannot understand:
  # 1 integer days since epoch (or since any other date);
  # 2 dates formatted as %d.%m.%y (in strptime format strings)
  print(hdf.summary())

  print('adding date columns')
  # NB: h2o automagically recognizes and if it doesn't recognize, you're out of luck
  hdf["ds5"] = hdf["ds5"].as_date("%d.%m.%y %H:%M")
  hdf["ds6"] = hdf["ds6"].as_date("%d.%m.%Y %H:%M:%S")
  ## these two cols are now detected as dates
  # hdf["ds7"] = hdf["ds7"].as_date("%m/%d/%y")
  # hdf["ds8"] = hdf["ds8"].as_date("%m/%d/%Y")
  hdf["ds9"] = hdf["ds9"].asfactor().as_date("%Y%m%d")
  hdf["ds10"] = hdf["ds10"].as_date("%Y_%m_%d")

  print('extracting year and month from posix date objects')
  hdf["year2"] = hdf["ds2"].year()
  hdf["year3"] = hdf["ds3"].year()
  hdf["year4"] = hdf["ds4"].year()
  hdf["year5"] = hdf["ds5"].year()
  hdf["year6"] = hdf["ds6"].year()
  hdf["year7"] = hdf["ds7"].year()
  hdf["year8"] = hdf["ds8"].year()
  hdf["year9"] = hdf["ds9"].year()
  hdf["year10"] = hdf["ds10"].year()
  hdf["mon2"] = hdf["ds2"].month()
  hdf["mon3"] = hdf["ds3"].month()
  hdf["mon4"] = hdf["ds4"].month()
  hdf["mon5"] = hdf["ds5"].month()
  hdf["mon6"] = hdf["ds6"].month()
  hdf["mon7"] = hdf["ds7"].month()
  hdf["mon8"] = hdf["ds8"].month()
  hdf["mon9"] = hdf["ds9"].month()
  hdf["mon10"] = hdf["ds10"].month()
  hdf["idx2"] = hdf["ds2"].year() * 12 + hdf["ds2"].month()
  hdf["idx3"] = hdf["ds3"].year() * 12 + hdf["ds3"].month()
  hdf["idx4"] = hdf["ds4"].year() * 12 + hdf["ds4"].month()
  hdf["idx5"] = hdf["ds5"].year() * 12 + hdf["ds5"].month()
  hdf["idx6"] = hdf["ds6"].year() * 12 + hdf["ds6"].month()
  hdf["idx7"] = hdf["ds7"].year() * 12 + hdf["ds7"].month()
  hdf["idx8"] = hdf["ds8"].year() * 12 + hdf["ds8"].month()
  hdf["idx9"] = hdf["ds9"].year() * 12 + hdf["ds9"].month()
  hdf["idx10"] = hdf["ds10"].year() * 12 + hdf["ds10"].month()

  # frames
  hdf2 = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/v-11-eurodate.csv"))
  hdf2["ds9"] = hdf2["ds9"].asfactor()

  hdf5 = hdf2["ds5"]
  hdf6 = hdf2["ds6"]
  hdf7 = hdf2["ds7"]
  hdf8 = hdf2["ds8"]
  hdf9 = hdf2["ds9"]
  hdf10 = hdf2["ds10"]

  print(hdf5.as_date("%d.%m.%y %H:%M"))
  print(hdf6.as_date("%d.%m.%Y %H:%M:%S"))
  print(hdf7)
  print(hdf8)
  print(hdf9.as_date("%Y%m%d"))
  print(hdf10.as_date("%Y_%m_%d"))

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_as_data)
else:
  test_as_data()
