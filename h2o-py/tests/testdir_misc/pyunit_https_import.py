from tests import pyunit_utils
import sys
sys.path.insert(1, "../../")
import h2o

def https_import():
    
    

    url = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"
    aa = h2o.import_frame(path=url)
    aa.show()

if __name__ == "__main__":
	pyunit_utils.standalone_test(https_import)
else:
	https_import()
