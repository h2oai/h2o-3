import sys
sys.path.insert(1, "../../")
import h2o

def http_import(ip,port):
    
    

    url = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"
    aa = h2o.import_file(path=url)
    aa.show()

if __name__ == "__main__":
    h2o.run_test(sys.argv, http_import)
