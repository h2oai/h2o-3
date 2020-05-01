import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator


def test_pubdev_6686():
    train = h2o.import_file(path="http://h2o-public-test-data.s3.amazonaws.com/smalldata/jira/pubdev_6686.csv")

    # Start a "long" running model that needs at least one column to be adapted (categorical_encoding="enum_limited")
    rf1 = H2ORandomForestEstimator(nfolds=3, ntrees=100, max_depth=10, categorical_encoding="enum_limited")
    rf1.start(y="model_pred", x=train.names.remove("y"), training_frame=train)

    # Start a model that finishes quickly and calls clean-up
    rf2 = H2ORandomForestEstimator(ntrees=1, max_depth=2)
    rf2.start(y="model_pred", x=train.names.remove("y"), training_frame=train)
    
    # Both models should finish without errors
    rf2.join()
    rf1.join()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_pubdev_6686)
else:
    test_pubdev_6686()
