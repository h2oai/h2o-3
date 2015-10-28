import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def as_python_test():
  
  

  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))
  airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k.zip"))

  iris.show()
  prostate.show()
  airlines.show()


  print h2o.as_list(iris)

  print h2o.as_list(prostate)

  print h2o.as_list(airlines)



if __name__ == "__main__":
  pyunit_utils.standalone_test(as_python_test)
else:
  as_python_test()