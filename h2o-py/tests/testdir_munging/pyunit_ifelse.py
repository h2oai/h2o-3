import sys
sys.path.insert(1, "../../")
import h2o, tests

def insert_missing():
  air_path = [tests.locate("smalldata/airlines/allyears2k_headers.zip")]

  data = h2o.import_file(path=air_path)

  hour1 = data["CRSArrTime"] / 100
  mins1 = data["CRSArrTime"] % 100
  arrTime = hour1*60 + mins1

  hour2 = data["CRSDepTime"] / 100
  mins2 = data["CRSDepTime"] % 100
  depTime = hour2*60 + mins2

  data["TravelTime"] = ((arrTime-depTime)>0).ifelse((arrTime-depTime),float("nan"))[0]

  data.show()

if __name__ == "__main__":
  tests.run_test(sys.argv, insert_missing)
