from __future__ import print_function

import warnings
from builtins import range
import sys, os
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

    for v in sys.modules.values():
        if getattr(v, '__warningregistry__', None):
            v.__warningregistry__ = {}
    with warnings.catch_warnings(record=True) as ws:
        # Get all RuntimeWarnings, copy from TomasF, thanks!
        warnings.simplefilter("always", RuntimeWarning)
        model = H2OGeneralizedLinearEstimator(family="Gaussian", lambda_search=True, Lambda=[0.01])
        model.train(x=X, y=Y, training_frame=training_data)

        assert any((issubclass(w.category, RuntimeWarning) and 'disabled when user specified any lambda value(s)' in str(w.message) 
                    for w in ws))

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_lambda_warning)
else:
    test_lambda_warning()
