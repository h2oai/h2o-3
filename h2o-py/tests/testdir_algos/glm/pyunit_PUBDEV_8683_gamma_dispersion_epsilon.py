from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


# This test is used to make sure user can specify dispersion_epsilon when building dispersion parameter estimates
# using maximum likelihood.
def test_dispersion_epsilon():
    training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True, dispersion_factor_method="ml")
    model.train(training_frame=training_data, x=x, y=Y)
    model_short = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True,
                                                  dispersion_factor_method="ml", dispersion_epsilon=1e-1)
    model_short.train(training_frame=training_data, x=x, y=Y)
    model_long = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True,
                                                dispersion_factor_method="ml", dispersion_epsilon=1e-4)
    model_long.train(training_frame=training_data, x=x, y=Y)
    true_dispersion_factor = 9
    assert abs(true_dispersion_factor-model_long._model_json["output"]["dispersion"]) <= abs(model_short._model_json["output"]["dispersion"]-true_dispersion_factor), \
    "H2O dispersion parameter estimate with epsilon 1r-4 {0} is worse than that of dispersion_epsilon 0.1 {1}.  True dispersion parameter is " \
    "{2}".format( model_long._model_json["output"]["dispersion"], model_short._model_json["output"]["dispersion"], true_dispersion_factor)



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_dispersion_epsilon)
else:
    test_dispersion_epsilon()
