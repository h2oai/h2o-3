from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators import H2OXGBoostEstimator


def h2omodels():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    response = 3

    model1 = H2OGeneralizedLinearEstimator(family="binomial")
    model1.train(y=response, training_frame=training_data)
    model2 = H2OXGBoostEstimator()
    model2.train(y=response, training_frame=training_data)
    models = h2o.models()
    assert model1.model_id == models[0]
    assert model2.model_id == models[1]


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2omodels)
else:
    h2omodels()
