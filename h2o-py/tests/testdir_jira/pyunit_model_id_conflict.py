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
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/pubdev_6686.csv"))

    # Start bunch of models to run concurrently
    rfs = []
    for i in range(10):
        print(i)
        rfs.append(start_model(train))

    # Parallel built models can fail, no guarantees
    successful = []
    for m in rfs:
        try:
            m.join()
            successful.append(m)
        except:
            pass

    # At least one has to succeed (typically the first one submitted)
    assert len(successful) > 0

    # A new model to overwrite the old one has to succeed
    start_model(train).join()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_conflicting_model_id)
else:
    test_conflicting_model_id()
