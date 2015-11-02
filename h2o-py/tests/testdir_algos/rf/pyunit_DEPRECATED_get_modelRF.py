import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def iris_get_model():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    model = h2o.random_forest(y=iris[4], x=iris[0:4], ntrees=50)
    model.show()

    model = h2o.get_model(model._id)
    model.show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(iris_get_model)
else:
    iris_get_model()
