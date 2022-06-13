from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# This test is used to make sure when a user tries to set alpha in the hyper-parameter of gridsearch.
def test_max_iterations_dispersion():
    training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']

    model_short = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True,
                                                  dispersion_factor_method="ml",max_iterations_dispersion=1)
    model_short.train(training_frame=training_data, x=x, y=Y)

    model_long = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True,
                                                dispersion_factor_method="ml",max_iterations_dispersion=1000000)
    model_long.train(training_frame=training_data, x=x, y=Y)
    true_dispersion=9
    # check model with more iterations should generate dispersion parameters closer to the true dispersion value
    assert abs(model_short._model_json["output"]["dispersion"]-true_dispersion) > \
           abs(model_long._model_json["output"]["dispersion"]-true_dispersion), \
        " Model with more iterations should generate better dispersion parameter estimate but did not."
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_max_iterations_dispersion)
else:
    test_max_iterations_dispersion()
