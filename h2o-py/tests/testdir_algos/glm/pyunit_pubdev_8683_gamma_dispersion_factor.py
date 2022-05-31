import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_gamma_dispersion_factor():
    training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True, dispersion_factor_method="ml")
    model.train(training_frame=training_data, x=x, y=Y)
    model_pearson = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True, 
                                                  dispersion_factor_method="pearson")
    model_pearson.train(training_frame=training_data, x=x, y=Y)
    true_dispersion_factor = 9
    R_dispersion_factor = 9.3
    dispersion_factor_estimated = model._model_json["output"]["dispersion"]
    dispersion_factor_estimated_pearson = model_pearson._model_json["output"]["dispersion"]
    print("True dispersion parameter {0}.  Estiamted ml dispersion parameter {1}.  Estimated pearson dispersion "
          "parameter {2}.".format(true_dispersion_factor, dispersion_factor_estimated, 
                                  dispersion_factor_estimated_pearson))
    assert abs(true_dispersion_factor-dispersion_factor_estimated) <= abs(R_dispersion_factor-true_dispersion_factor),\
        "H2O dispersion parameter estimate {0} is worse than that of R {1}.  True dispersion parameter is " \
        "{2}".format( dispersion_factor_estimated, R_dispersion_factor, true_dispersion_factor)
    assert abs(true_dispersion_factor-dispersion_factor_estimated) <= abs(dispersion_factor_estimated_pearson-true_dispersion_factor), \
        "H2O dispersion parameter ml estimate {0} is worse than that of H2O dispersion parameter pearson estimate {1}." \
        "  True dispersion parameter is {2}".format( dispersion_factor_estimated, dispersion_factor_estimated_pearson,
                                                     true_dispersion_factor)


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_gamma_dispersion_factor)
else:
    test_gamma_dispersion_factor()
