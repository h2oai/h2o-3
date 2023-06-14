import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def test_tweedie_p_and_phi_estimation_2p6_disp2_est():
    training_data = h2o.import_file(
        pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p2p6_disp2_5Cols_10krows_est1p94.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    trueDisp = 2.6
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             tweedie_variance_power=trueDisp,
                                             lambda_=0, compute_p_values=False,
                                             init_dispersion_parameter=2.0,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 2.6, phi = 2 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # p = 2.6, phi = 2 converged to p = 2.6724208506680243 ; phi = 1.9585751651721317
    # Estimate from R tweedie.profile (using p with step 0.001): p= 2.671429 ;  phi= 1.958288 
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.6724) < 2e-4
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 1.9585) < 2e-4


def test_tweedie_p_and_phi_estimation_3_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p3_phi1_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 3, phi = 1 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # p = 3, phi = 1 converged to p = 2.9892180905617645 ; phi = 0.9918345933463155
    # Estimate from R tweedie.profile (using p with step 0.001): p= 2.988776 ;  phi= 0.991949
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.98921) < 2e-4
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 0.9918) < 2e-4


def test_tweedie_p_and_phi_estimation_5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p5_phi1_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 5, phi = 1 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # p = 5, phi = 1 converged to p = 5.027117009572835 ; phi = 1.0103477213453012
    # Estimate from R tweedie.profile (using p with step 0.001): p= 5.027143 ;  phi= 1.010372 
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 5.02711) < 2e-4
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 1.01034) < 2e-4


def test_tweedie_p_and_phi_estimation_5_phi_0p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p5_phi0p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=0.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 5, phi = 0.5 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # p = 5, phi = 0.5 converged to p = 4.9431171179656594 ; phi = 0.4880765893343399
    # Estimate from R tweedie.profile (using p with step 0.001): p= 4.943061 ;  phi= 0.4880817 
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 4.94311) < 2e-4
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 0.488076) < 2e-4


def test_tweedie_p_and_phi_estimation_3_phi_1p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p3_phi1p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=1.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 3, phi = 1.5 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # p = 3, phi = 1.5 converged to p = 2.9970294935158193 ; phi = 1.481355462371787
    # Estimate from R tweedie.profile (using p with step 0.001): p= 2.996939 ;  phi= 1.481323 
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.9970) < 2e-4
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 1.481355) < 2e-4


def test_tweedie_p_and_phi_estimation_3_phi_0p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p3_phi0p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=0.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 3, phi = 0.5 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # p = 3, phi = 0.5 converged to p = 3.003833130003513 ; phi = 0.5021598824912168
    # Estimate from R tweedie.profile (using p with step 0.001): p= 3.003061 ;  phi= 0.502445 
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0038) < 2e-4
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 0.50215) < 2e-4


def test_tweedie_p_and_phi_estimation_2p5_phi_2p5_no_link_power_est():
    training_data = h2o.import_file(
        pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p2p5_phi2p5_5Cols_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=False,
                                             fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=2.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("p = 2.5, phi = 2.5 converged to p =", model_ml.actual_params["tweedie_variance_power"], "; phi =",
          model_ml.actual_params["init_dispersion_parameter"])
    # p = 2.5, phi = 2.5 converged to p = 2.5928359533723895 ; phi = 2.6301240916926067
    # Estimate from R tweedie.profile (using p with step 0.001): p= 2.589796 ;  phi= 2.618605 
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.592835) < 2e-4
    assert abs(model_ml.actual_params["init_dispersion_parameter"] - 2.63012) < 2e-4


def measure_time(t):
    def _():
        import time
        start = time.monotonic()
        t()
        print(f"The {t.__name__} took {time.monotonic() - start}s")

    _.__name__ = t.__name__
    return _


def run_random_test():
    import random
    tests = [
        test_tweedie_p_and_phi_estimation_2p6_disp2_est,  # 1547.11469818s -> 1418.9195188410001s -> 613.7193842849999s
        test_tweedie_p_and_phi_estimation_2p5_phi_2p5_no_link_power_est,  # 1700.5806319029998s -> 1537.708712792s -> 752.2378191370001s
        test_tweedie_p_and_phi_estimation_3_phi_0p5_no_link_power_est,  # 2992.1222389420004s ->  2801.2481954240006s -> 1314.5836838070002s
        test_tweedie_p_and_phi_estimation_3_no_link_power_est,  # 2969.200690304s ->  3250.5404198899987s ->  1438.913372169s
        test_tweedie_p_and_phi_estimation_3_phi_1p5_no_link_power_est,  # 1295.719605385002s -> 1491.1029589709997s -> 789.1963182149993s
        test_tweedie_p_and_phi_estimation_5_no_link_power_est,  # 2117.0419905989984s -> 2520.0094414819996s -> 1221.1567296999992s
        test_tweedie_p_and_phi_estimation_5_phi_0p5_no_link_power_est,  # 2783.940797473999s -> 2976.205751714s -> 1513.149319389s
    ]
    return [random.choice(tests)]


pyunit_utils.run_tests(run_random_test())
