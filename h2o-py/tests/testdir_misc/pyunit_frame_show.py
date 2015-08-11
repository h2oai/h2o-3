import sys
sys.path.insert(1, "../../")
import h2o

def frame_show(ip,port):
    
    

    iris = h2o.import_file(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    prostate = h2o.import_file(path=h2o.locate("smalldata/prostate/prostate.csv.zip"))
    airlines = h2o.import_file(path=h2o.locate("smalldata/airlines/allyears2k.zip"))

    iris.show()
    prostate.show()
    airlines.show()

if __name__ == "__main__":
    h2o.run_test(sys.argv, frame_show)
