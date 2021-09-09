from __future__ import print_function

from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils


def test_binomial_response_warning():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"))
    Y = "survived"
    X = ["name", "sex"]

    with pyunit_utils.catch_warnings() as ws:
        model = H2OGradientBoostingEstimator(ntrees=1)
        model.train(x=X, y=Y, training_frame=training_data)
        assert pyunit_utils.contains_warning(ws, 'Response is numeric, so the regression model will be trained. However, the cardinality is equaled to two, so if you want to train a classification model, convert the response column to categorical before training.')
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_binomial_response_warning)
else:
    test_binomial_response_warning()
