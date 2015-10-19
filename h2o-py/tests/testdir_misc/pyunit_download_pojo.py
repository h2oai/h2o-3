

import h2o, tests

def download_pojo():
  
  

  iris = h2o.import_file(path=tests.locate("smalldata/iris/iris_wheader.csv"))
  print "iris:"
  iris.show()

  m = h2o.gbm(x=iris[:4],y=iris[4])
  h2o.download_pojo(m)



pyunit_test = download_pojo
