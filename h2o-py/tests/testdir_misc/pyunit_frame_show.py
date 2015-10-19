

import h2o, tests

def frame_show():
    
    

    iris = h2o.import_file(path=tests.locate("smalldata/iris/iris_wheader.csv"))
    prostate = h2o.import_file(path=tests.locate("smalldata/prostate/prostate.csv.zip"))
    airlines = h2o.import_file(path=tests.locate("smalldata/airlines/allyears2k.zip"))

    iris.show()
    prostate.show()
    airlines.show()


pyunit_test = frame_show
