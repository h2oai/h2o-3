import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def http_import():
    
    

    url = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"
    aa = h2o.import_file(path=url)
    aa.show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(http_import)
else:
    http_import()