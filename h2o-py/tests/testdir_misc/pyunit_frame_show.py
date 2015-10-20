



def frame_show():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k.zip"))

    iris.show()
    prostate.show()
    airlines.show()


frame_show()
