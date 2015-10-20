



def pubdev_1696():
    

    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))

    try:
        h2o.gbm(x=iris[0:3], y=iris[3], nfolds=-99)
        assert False, "expected an error"
    except EnvironmentError:
        assert True


pubdev_1696()
