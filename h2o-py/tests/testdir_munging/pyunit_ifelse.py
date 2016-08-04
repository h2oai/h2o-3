from __future__ import division
from past.utils import old_div
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def insert_missing():
    air_path = pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip")

    data = h2o.import_file(path=air_path)

    hour1 = old_div(data["CRSArrTime"], 100)
    mins1 = data["CRSArrTime"] % 100
    arr_time = hour1 * 60 + mins1

    hour2 = old_div(data["CRSDepTime"], 100)
    mins2 = data["CRSDepTime"] % 100
    dep_time = hour2 * 60 + mins2

    data["TravelTime"] = ((arr_time - dep_time) > 0).ifelse((arr_time - dep_time), float("nan"))[0]

    data.show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(insert_missing)
else:
    insert_missing()
