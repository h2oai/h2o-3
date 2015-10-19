import h2o, tests

def deeplearning_basic():
    

    iris_hex = h2o.import_file(path=tests.locate("smalldata/iris/iris.csv"))
    hh = h2o.deeplearning(x=iris_hex[:3],
                          y=iris_hex[4],
                          loss='CrossEntropy')
    hh.show()

pyunit_test = deeplearning_basic
