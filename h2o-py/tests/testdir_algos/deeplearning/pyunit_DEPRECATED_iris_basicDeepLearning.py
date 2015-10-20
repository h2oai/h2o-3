

def deeplearning_basic():
    

    iris_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    hh = h2o.deeplearning(x=iris_hex[:3],
                          y=iris_hex[4],
                          loss='CrossEntropy')
    hh.show()

deeplearning_basic()
