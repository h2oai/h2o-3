import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_gamma_dispersion_parameter_deviance():
    training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']

    model = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True, dispersion_parameter_method="deviance")
    model.train(training_frame=training_data, x=x, y=Y)
    trueDispersion = 9
    modelML = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True, dispersion_parameter_method="ml")
    modelML.train(training_frame=training_data, x=x, y=Y)
    assert abs(trueDispersion-modelML._model_json["output"]["dispersion"]) <\
           abs(trueDispersion-model._model_json["output"]["dispersion"])
    

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_gamma_dispersion_parameter_deviance)
else:
    test_gamma_dispersion_parameter_deviance()
