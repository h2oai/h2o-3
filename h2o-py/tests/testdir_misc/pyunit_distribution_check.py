import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator

# PUBDEV-4622 quasibinomial distributions are implemented for GLM and GBM.
# This test is to make sure other algos cannot specify quasibinomial as its distribution

def metric_json_check():
    iris_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    try:
        hh = H2ODeepLearningEstimator(hidden=[], loss="CrossEntropy", export_weights_and_biases=True, distribution='quasibinomial')
        hh.train(x=list(range(4)), y=4, training_frame=iris_hex)
        assert False, "Deeplearning should have thrown an error since Quasibinomial is not supported at this time."
    except:
        print("Quasibinomial is not supported for deeplearning.")

    try:
        model = H2ORandomForestEstimator(ntrees=50, max_depth=100, distribution='quasibinomial')
        model.train(y=4, x=list(range(4)), training_frame=iris)
        assert False, "DRF should have thrown an error since Quasibinomial is not supported at this time."
    except:
        print("Quasibinomial is not supported for deeplearning.")

if __name__ == "__main__":
    pyunit_utils.standalone_test(metric_json_check)
else:
    metric_json_check()
