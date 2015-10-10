import sys
sys.path.insert(1, "../../")
import h2o, tests

def download_pojo():
  
  

  iris = h2o.import_file(path=tests.locate("smalldata/iris/iris_wheader.csv"))
  print "iris:"
  iris.show()

  m = h2o.gbm(x=iris[:4],y=iris[4])
  h2o.download_pojo(m)


if __name__ == "__main__":
  tests.run_test(sys.argv, download_pojo)
