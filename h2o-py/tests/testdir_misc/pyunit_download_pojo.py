import sys
sys.path.insert(1, "../../")
import h2o

def download_pojo(ip,port):
  
  

  iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
  print "iris:"
  iris.show()

  m = h2o.gbm(x=iris[:4],y=iris[4])
  h2o.download_pojo(m)


if __name__ == "__main__":
  h2o.run_test(sys.argv, download_pojo)
