



def iris_all():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris2.csv"))

    model = h2o.random_forest(y=iris[4], x=iris[0:4], ntrees=50, max_depth=100)
    model.show()


iris_all()

