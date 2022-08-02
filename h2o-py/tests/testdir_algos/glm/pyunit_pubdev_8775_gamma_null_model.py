import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_gamma_null_model():
    training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    training_data2 = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True, 
                                          dispersion_parameter_method="ml")
    model.train(training_frame=training_data, x=x, y=Y)
    coeffs = model.coef()
    model_null_ml = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True, 
                                                  dispersion_parameter_method="ml", build_null_model=True)
    model_null_ml.train(training_frame=training_data, x=x, y=Y)
    coeffs_null_ml = model_null_ml.coef()
    model_null_p = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True,
                                               dispersion_parameter_method="pearson", build_null_model=True)
    model_null_p.train(training_frame=training_data, x=x, y=Y)
    coeffs_null_p = model_null_p.coef()

    assert len(coeffs) > len(coeffs_null_ml), "Full model coefficient length: {0} shall exceed null model coefficient" \
                                           " length: {1}".format(len(coeffs), len(coeffs_null_ml))
    assert len(coeffs_null_ml) == 1, "Null model from ml coefficient length: {0} shall be 1.".format(len(coeffs_null_ml))
    assert len(coeffs_null_p) == 1, "Null model from pearson coefficient length: {0} shall be " \
                                          "1.".format(len(coeffs_null_p))
    assert 'Intercept' in coeffs_null_ml.keys(), "Null model from ml should contain Intercept as its only coefficient" \
                                                 " but did not."
    assert 'Intercept' in coeffs_null_p.keys(), "Null model from pearson should contain Intercept as its only" \
                                                " coefficient but did not."

    # make sure it can predict with null model
    pred_ml = model_null_ml.predict(training_data)
    pred_p = model_null_p.predict(training_data2)
    pyunit_utils.compare_frames_local(pred_p[0], pred_ml[0], prob=1)
    
if __name__ == "__main__":
  pyunit_utils.standalone_test(test_gamma_null_model)
else:
    test_gamma_null_model()
