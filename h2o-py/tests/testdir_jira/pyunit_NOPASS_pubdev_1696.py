import sys
sys.path.insert(1, "../../")
import h2o

def pubdev_1696(ip, port):
    h2o.init(ip, port)

    iris = h2o.import_frame(h2o.locate("smalldata/iris/iris.csv"))
    model = h2o.gbm(x=iris[0:3], y=iris[3], nfolds=-99)

if __name__ == "__main__":
    h2o.run_test(sys.argv, pubdev_1696)