import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def download_pojo():
  
  

  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  print "iris:"
  iris.show()

  m = h2o.gbm(x=iris[:4],y=iris[4])
  h2o.download_pojo(m)




if __name__ == "__main__":
  pyunit_utils.standalone_test(download_pojo)
else:
  download_pojo()
