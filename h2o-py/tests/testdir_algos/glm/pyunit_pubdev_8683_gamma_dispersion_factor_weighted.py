import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# add weight column
def test_gamma_dispersion_parameter():
    training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    weight = pyunit_utils.random_dataset_real_only(training_data.nrow, 1, realR=2, misFrac=0, randSeed=12345)
    weight = weight.abs()
    training_data = training_data.cbind(weight)
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model_ml = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, compute_p_values=True, dispersion_parameter_method="ml", 
                                          weights_column = "abs(C1)")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    true_dispersion_factor = 9
    R_dispersion_factor = 9.3
    dispersion_factor_ml_estimated = model_ml._model_json["output"]["dispersion"]
    print("True dispersion parameter {0}.  Estimated ml dispersion parameter {1}"
          ".".format(true_dispersion_factor, dispersion_factor_ml_estimated))
    assert abs(true_dispersion_factor-dispersion_factor_ml_estimated) <= abs(R_dispersion_factor-true_dispersion_factor),\
        "H2O dispersion parameter ml estimate {0} is worse than that of R {1}.  True dispersion parameter is " \
        "{2}".format( dispersion_factor_ml_estimated, R_dispersion_factor, true_dispersion_factor)   

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_gamma_dispersion_parameter)
else:
    test_gamma_dispersion_parameter()
