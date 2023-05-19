import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def test_tweedie_p_and_phi_estimation_1p2_phi_2_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p1p2_phi2_5Cols_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 1.2, phi = 2 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # p = 1.2, phi = 2 converged to p = 1.1932493756749825 ; phi = 2.0191183402978257
    # Estimate from R tweedie.profile (using p with step 0.001; but different link power (R didn't converge with the same as in h2o)): p= 1.195102 ;  phi= 2.032903 
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.19325) < 0.001
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 2.01912) < 0.001
    assert abs(
        model_ml.actual_params["init_dispersion_parameter"] - model_ml._model_json["output"]["dispersion"]) < 1e-16


def test_tweedie_p_and_phi_estimation_1_8_no_link_power_est():
    training_data = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/tweedie_1p8Power_2Dispersion_5Col_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.8,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                             max_iterations_dispersion=1000)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 1.8, phi = 2 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # p = 1.8, phi = 2 converged to p = 1.7981745663886501 ; phi = 1.9890117211840974
    # Estimate from R tweedie.profile (using p with step 0.001; but different link power (R didn't converge with the same as in h2o)): p= 1.798571 ;  phi= 1.994105 
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.7981745663886501) < 0.001
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 1.9890117211840974) < 0.001
    assert abs(
        model_ml.actual_params["init_dispersion_parameter"] - model_ml._model_json["output"]["dispersion"]) < 1e-16


def test_tweedie_p_and_phi_estimation_2p1_disp2_est():
    training_data = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/tweedie_p2p1_disp3_5Cols_10kRows_est1p89.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             lambda_=0, compute_p_values=False, tweedie_variance_power=1.5,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 2.1, phi = 2 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # Estimate from R tweedie.profile: p= 2.163265 ;  phi= 2.62834 
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.1632) < 2e-4
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 2.62834) < 2e-4
    assert abs(
        model_ml.actual_params["init_dispersion_parameter"] - model_ml._model_json["output"]["dispersion"]) < 1e-16


def measure_time(t):
    def _():
        import time
        start = time.monotonic()
        t()
        print(f"The {t.__name__} took {time.monotonic() - start}s")

    _.__name__ = t.__name__
    return _


pyunit_utils.run_tests([
    test_tweedie_p_and_phi_estimation_1p2_phi_2_no_link_power_est,  # 8.461037509999999s -> 7.163699680999999s
    test_tweedie_p_and_phi_estimation_1_8_no_link_power_est,  # 452.663033281s ->  10.295818489000002s
    test_tweedie_p_and_phi_estimation_2p1_disp2_est,  # 127.51284229400001s -> 113.20302847699999s
])
