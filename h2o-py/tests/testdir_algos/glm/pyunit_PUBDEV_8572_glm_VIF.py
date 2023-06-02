import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import math

def test_vif_tweedie():
    training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, generate_variable_inflation_factors=True)
    model.train(training_frame=training_data, x=x, y=Y)
    vif_names = model.get_variable_inflation_factors()
    vif = model._model_json["output"]["variable_inflation_factors"]
    vif_predictor_names = model._model_json["output"]["vif_predictor_names"]
    # check variable inflation factors are the same gotten from the coefficient tables and from the variables directly
    count = 0
    for pred in vif_predictor_names:
        if math.isnan(vif[count]):
            assert math.isnan(vif_names[pred]), "For predictor: {0}, expected inflation variable factor is NaN but" \
                                                " actual value is {1} and is not NaN.".format(pred, vif_names[pred])
        else:
            assert abs(vif[count]-vif_names[pred]) < 1e-6, "For predictor: {0}, expected inflation variable factor:" \
                                                           " {1}, actual value: {2}".format(pred, vif_names[pred], vif[count])
        count = count+1
    count_non_nan = 0
    for pred in vif_names.keys():
        if not(math.isnan(vif_names[pred])):
            count_non_nan += 1
    assert count_non_nan == len(vif), "Expected numerical predictor length: {0}, actual: {1}.  They do not " \
                                          "match!".format(len(vif), count_non_nan)

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_vif_tweedie)
else:
    test_vif_tweedie()
