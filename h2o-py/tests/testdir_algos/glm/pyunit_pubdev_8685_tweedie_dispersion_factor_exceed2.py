import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def test_tweedie_dispersion_factor():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p3_phi1_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    
    # train ml model with initial guess below the true disperion value
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', lambda_=0, compute_p_values=True,
                                             tweedie_variance_power=3,
                                             init_dispersion_parameter=0.5, dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    # train ml mode with initial guess above the true dispersion value
    model_ml2 = H2OGeneralizedLinearEstimator(family='tweedie', lambda_=0, compute_p_values=True,
                                              tweedie_variance_power=3,
                                              init_dispersion_parameter=1.5, dispersion_parameter_method="ml")
    model_ml2.train(training_frame=training_data, x=x, y=Y)
    
    model_pearson = H2OGeneralizedLinearEstimator(family='tweedie', lambda_=0, compute_p_values=True,
                                                  tweedie_variance_power=3,
                                                  dispersion_parameter_method="pearson")
    model_pearson.train(training_frame=training_data, x=x, y=Y)
    true_dispersion_factor = 1.0

    dispersion_parameter_estimated = model_ml._model_json["output"]["dispersion"]
    dispersion_parameter_estimated2 = model_ml2._model_json["output"]["dispersion"]
    dispersion_parameter_estimated_pearson = model_pearson._model_json["output"]["dispersion"]
    print("True dispersion parameter {0}.  Estimated ml dispersion parameter {1}.  Estimated pearson dispersion "
          "parameter {2}".format(true_dispersion_factor, dispersion_parameter_estimated,
                                  dispersion_parameter_estimated_pearson))
    # make sure the ml estimates are closer to the true dispersion value than the dispersion value from pearson
    assert abs(true_dispersion_factor - dispersion_parameter_estimated) <= abs(
        dispersion_parameter_estimated_pearson - true_dispersion_factor), \
        "H2O dispersion parameter ml estimate {0} is worse than that of H2O dispersion parameter pearson estimate {1}." \
        "  True dispersion parameter is {2}".format(dispersion_parameter_estimated,
                                                    dispersion_parameter_estimated_pearson,
                                                    true_dispersion_factor)
    assert abs(true_dispersion_factor - dispersion_parameter_estimated2) <= abs(
        dispersion_parameter_estimated_pearson - true_dispersion_factor), \
        "H2O dispersion parameter ml estimate {0} is worse than that of H2O dispersion parameter pearson estimate {1}." \
        "  True dispersion parameter is {2}".format(dispersion_parameter_estimated2,
                                                    dispersion_parameter_estimated_pearson,
                                                    true_dispersion_factor)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_tweedie_dispersion_factor)
else:
    test_tweedie_dispersion_factor()
