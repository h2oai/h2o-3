import sys
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_2041():

    iris = h2o.import_file(h2o.locate("smalldata/iris/iris.csv"))

    s = iris.runif(seed=12345)
    train1 = iris[s >= 0.5]
    train2 = iris[s <  0.5]

    m1 = h2o.deeplearning(x=train1[0:4], y=train1[4], epochs=100)

    # update m1 with new training data
    m2 = h2o.deeplearning(x=train2[0:4], y=train2[4], epochs=200, checkpoint=m1.id)

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_2041)
