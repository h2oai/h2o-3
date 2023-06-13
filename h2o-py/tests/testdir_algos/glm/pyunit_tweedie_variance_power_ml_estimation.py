import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def test_tweedie_var_power_estimation_1p2_phi_2_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p1p2_phi2_5Cols_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=2,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.1975634809085063) < 0.001
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.2671241774599327) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=2,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.1956740204906753) < 0.001
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.2671241774599327) < 0.001


def test_tweedie_var_power_estimation_1_8_no_link_power_est():
    training_data = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/tweedie_1p8Power_2Dispersion_5Col_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model_18 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.8, dispersion_learning_rate=1,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                             max_iterations_dispersion=1000)
    model_18.train(training_frame=training_data, x=x, y=Y)
    print("p = 1.8 converged to p =", model_18.actual_params["tweedie_variance_power"])
    assert abs(model_18.actual_params["tweedie_variance_power"] - 1.7988226798952265) < 1e-4
    
    model_11 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.1, dispersion_learning_rate=1,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                             max_iterations_dispersion=1000)
    model_11.train(training_frame=training_data, x=x, y=Y)
    print("p = 1.1 converged to p =", model_11.actual_params["tweedie_variance_power"])
    assert abs(model_11.actual_params["tweedie_variance_power"] - model_18.actual_params["tweedie_variance_power"]) < 1e-3

    model_201 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                              tweedie_variance_power=2.01, dispersion_learning_rate=1,
                                              lambda_=0, compute_p_values=False,
                                              dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                              max_iterations_dispersion=1000)
    model_201.train(training_frame=training_data, x=x, y=Y)
    print("p = 2.01 converged to p =", model_201.actual_params["tweedie_variance_power"])
    assert abs(model_11.actual_params["tweedie_variance_power"] - model_201.actual_params["tweedie_variance_power"]) < 1e-3

    model_21 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.1, dispersion_learning_rate=1,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                             max_iterations_dispersion=1000)
    model_21.train(training_frame=training_data, x=x, y=Y)
    print("p = 2.1 converged to p =", model_21.actual_params["tweedie_variance_power"])
    assert abs(model_11.actual_params["tweedie_variance_power"] - model_21.actual_params["tweedie_variance_power"]) < 1e-3

    model_25 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.5, dispersion_learning_rate=1,
                                             lambda_=0, compute_p_values=True,
                                             dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                             max_iterations_dispersion=1000)
    model_25.train(training_frame=training_data, x=x, y=Y)
    print("p = 2.5 converged to p =", model_25.actual_params["tweedie_variance_power"])
    assert abs(model_11.actual_params["tweedie_variance_power"] - model_25.actual_params["tweedie_variance_power"]) < 1e-3


pyunit_utils.run_tests([
    test_tweedie_var_power_estimation_1p2_phi_2_no_link_power_est,
    test_tweedie_var_power_estimation_1_8_no_link_power_est,
])
