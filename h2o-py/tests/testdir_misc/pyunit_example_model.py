from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def train_example_model():
    prostate_frame = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))

    example = None
    try:
        from h2o.estimators.example import H2OExampleEstimator
        example = H2OExampleEstimator(max_iterations=42)
    except ImportError as error:
        print(error)
    
    if example is None:
        print("This build doesn't have the example estimator built-in")
        return

    example.train(training_frame=prostate_frame)

    assert example._model_json["output"]["maxs"] == [380.0, 1.0, 79.0, 2.0, 4.0, 2.0, 139.7, 97.6, 9.0]


if __name__ == "__main__":
    pyunit_utils.standalone_test(train_example_model)
else:
    train_example_model()
