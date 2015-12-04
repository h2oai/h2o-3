from tests import pyunit_utils
import sys
sys.path.insert(1, "../../")
import h2o

def frame_show():
    
    

    iris = h2o.import_frame(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    prostate = h2o.import_frame(path=pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))

    airlines = h2o.import_frame(path=pyunit_utils.locate("smalldata/airlines/allyears2k.zip"))


    iris.show()
    prostate.show()
    airlines.show()

if __name__ == "__main__":
	pyunit_utils.standalone_test(frame_show)
else:
	frame_show()
