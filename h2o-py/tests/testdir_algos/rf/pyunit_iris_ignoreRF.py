import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def iris_ignore():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris2.csv"))
  
    for maxx in range(4):
      model = h2o.random_forest(y=iris[4], x=iris[range(maxx+1)], ntrees=50, max_depth=100)
      model.show()




if __name__ == "__main__":
    pyunit_utils.standalone_test(iris_ignore)
else:
    iris_ignore()
