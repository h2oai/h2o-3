import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def pubdev_1839():

    train = h2o.import_file(pyunit_utils.locate("smalldata/jira/pubdev_1839_repro_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/jira/pubdev_1839_repro_test.csv"))

    glm0 = H2OGeneralizedLinearEstimator(family="poisson")
    glm0.train(y="bikes",x=list(set(train.names) - set(["bikes"])), training_frame=train, validation_frame=test)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_1839)
else:
    pubdev_1839()
