import sys
sys.path.insert(1, "../../")
import h2o, tests

def http_import():
    
    

    url = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"
    aa = h2o.import_file(path=url)
    aa.show()

if __name__ == "__main__":
    tests.run_test(sys.argv, http_import)
