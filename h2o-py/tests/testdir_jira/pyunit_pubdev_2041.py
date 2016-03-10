import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator


def pubdev_2041():
    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    s = iris.runif(seed=12345)
    train1 = iris[s >= 0.5]
    train2 = iris[s < 0.5]

    m1 = H2ODeepLearningEstimator(epochs=100)
    m1.train(x=list(range(4)), y=4, training_frame=train1)

    # update m1 with new training data
    m2 = H2ODeepLearningEstimator(checkpoint=m1.model_id, epochs=200)
    m2.train(x=list(range(4)), y=4, training_frame=train2)

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_2041)
else:
    pubdev_2041()
