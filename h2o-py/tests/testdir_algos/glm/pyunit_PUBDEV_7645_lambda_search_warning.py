from __future__ import print_function

from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils


# This method test to make sure when a user set lambda_search=True and choose a lambda value,  a warning message 
# should be generated and passed to the user.
def test_lambda_warning():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/BostonHousing.csv"))
    Y = 13
    X = list(range(13))
    model = H2OGeneralizedLinearEstimator(family="Gaussian", lambda_search=True, Lambda=[0.01])
    model.train(x=X, y=Y, training_frame=training_data)

    with pyunit_utils.catch_warnings() as ws:
        model = H2OGeneralizedLinearEstimator(family="Gaussian", lambda_search=True, Lambda=[0.01])
        model.train(x=X, y=Y, training_frame=training_data)

        assert pyunit_utils.contains_warning(ws, 'disabled when user specified any lambda value(s)')


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_lambda_warning)
else:
    test_lambda_warning()
