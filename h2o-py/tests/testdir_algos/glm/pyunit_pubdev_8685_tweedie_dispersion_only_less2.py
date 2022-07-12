import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_gamma_dispersion_factor():
  #  training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_1p2Power_2Dispersion_5Col_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_tweedie_variance_power=True, tweedie_variance_power=1.2, 
                                          lambda_=0, compute_p_values=True, dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    ml_dispersion_parameter = model_ml._model_json["output"]["dispersion"]
    model_pearson = H2OGeneralizedLinearEstimator(family='tweedie', lambda_=0, compute_p_values=True, tweedie_variance_power=1.2,
                                                  dispersion_parameter_method="pearson")
    model_pearson.train(training_frame=training_data, x=x, y=Y)
    pearson_dispersion_parameter = model_pearson._model_json["output"]["dispersion"]
    true_dispersion_parameter = 2
    
    assert abs(true_dispersion_parameter - ml_dispersion_parameter) < abs(true_dispersion_parameter-pearson_dispersion_parameter), \
        "ML dispersion parameter estimated: {0}, Pearson dispersion parameter estimated: {1}.  True dispersion " \
        "parameter: {2}. ML performs worse than Pearson.".format(ml_dispersion_parameter, pearson_dispersion_parameter,
                                                                 true_dispersion_parameter)
  


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_gamma_dispersion_factor)
else:
    test_gamma_dispersion_factor()
