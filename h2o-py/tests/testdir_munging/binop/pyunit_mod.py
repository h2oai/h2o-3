import sys
sys.path.insert(1, "../../../")
import h2o, tests

def frame_as_list():
  
  

  prostate = h2o.import_file(path=h2o.locate("smalldata/prostate/prostate.csv.zip"))

  (prostate % 10).show()
  (prostate[4] % 10).show()


  airlines = h2o.import_file(path=h2o.locate("smalldata/airlines/allyears2k_headers.zip"))

  (airlines["CRSArrTime"] % 100).show()

if __name__ == "__main__":
  tests.run_test(sys.argv, frame_as_list)
