import sys
sys.path.insert(1, "../../")
import h2o

def as_python_test(ip,port):
  # Connect to h2o
  h2o.init(ip,port)

  iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
  prostate = h2o.import_frame(path=h2o.locate("smalldata/prostate/prostate.csv.zip"))
  airlines = h2o.import_frame(path=h2o.locate("smalldata/airlines/allyears2k.zip"))

  iris.show()
  prostate.show()
  airlines.show()


  print h2o.as_list(iris)

  print h2o.as_list(prostate)

  print h2o.as_list(airlines)

if __name__ == "__main__":
  h2o.run_test(sys.argv, as_python_test)
