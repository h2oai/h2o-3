import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def pub_445_long_request_uri():
    mnistTrain = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
    mnistTest = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))

    mnistTrain.set_name(col=784, name="label")
    mnistTest.set_name(col=784, name="label")

    mnistModel = H2OGradientBoostingEstimator(ntrees=2, max_depth=2)
    mnistModel.train(x=list(range(784)),y="label",training_frame=mnistTrain,validation_frame=mnistTest)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pub_445_long_request_uri)
else:
    pub_445_long_request_uri()
