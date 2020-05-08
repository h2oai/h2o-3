import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator


def start_model(train):
    rf = H2ORandomForestEstimator(nfolds=3, ntrees=10, max_depth=10, categorical_encoding="enum_limited",
                                  model_id="mateusz")
    rf.start(y="model_pred",
             x=train.names.remove("y"),
             training_frame=train)
    return rf


def test_conflicting_model_id():
    train = h2o.import_file(path="http://h2o-public-test-data.s3.amazonaws.com/smalldata/jira/pubdev_6686.csv")

    # Start the first model
    first_model = start_model(train)

    # Start some models to run in parallel with the first one
    rfs = []
    for i in range(10):
        print(i)
        rfs.append(start_model(train))

    # First model has to finish successfully
    first_model.join()

    # Parallel built models can fail, no guarantees
    for m in rfs:
        try:
            m.join()
        except:
            pass

    # A new model to overwrite the old one has to succeed
    start_model(train).join()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_conflicting_model_id)
else:
    test_conflicting_model_id()
