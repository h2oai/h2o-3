import sys
sys.path.insert(1, "../../../")
import h2o

def iris_ignore(ip,port):
    # Connect to h2o
    

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris2.csv"))
  
    for maxx in range(4):
      model = h2o.random_forest(y=iris[4], x=iris[range(maxx+1)], ntrees=50, max_depth=100)
      model.show()

if __name__ == "__main__":
  h2o.run_test(sys.argv, iris_ignore)

