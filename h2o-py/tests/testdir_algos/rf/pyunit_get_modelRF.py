



def iris_get_model():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    model = h2o.random_forest(y=iris[4], x=iris[0:4], ntrees=50)
    model.show()

    model = h2o.get_model(model._id)
    model.show()


iris_get_model()
