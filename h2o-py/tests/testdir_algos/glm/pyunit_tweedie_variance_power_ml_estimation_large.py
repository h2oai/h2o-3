import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def test_tweedie_var_power_estimation_3_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p3_phi1_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.995490728108107) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.9871973269136007) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.990370178297177) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.9871973269136007) < 0.001


def test_tweedie_var_power_estimation_5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p5_phi1_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 5.015919757985777) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 5.015919757985777) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 5.015901230435018) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 5.015901230435018) < 0.001


def test_tweedie_var_power_estimation_5_phi_0p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p5_phi0p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=0.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 4.9526804187281765) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 4.9526804187281765) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=0.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 4.952682388241566) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 4.952682388241566) < 0.001


def test_tweedie_var_power_estimation_3_phi_1p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p3_phi1p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=1.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0043941544956168) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.9983337600012767) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=1.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0005648582465207) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.9983337600012767) < 0.001


def test_tweedie_var_power_estimation_3_phi_0p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p3_phi0p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=0.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.029819714027204) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0632864030533775) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=0.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.014297969690198) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0064318296956998) < 0.001



def test_tweedie_var_power_estimation_2p5_phi_2p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p2p5_phi2p5_5Cols_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=2.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.565921555131599) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.567181728720017) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=2.5,
                                             lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.5657228160821295) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.567181728720017) < 0.001


def test_tweedie_var_power_estimation_2p1_disp2_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p2p1_disp3_5Cols_10kRows_est1p89.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    trueDisp = 2.1
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=2.1,
                                             lambda_=0, compute_p_values=False,
                                             init_dispersion_parameter=2.0,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("estimated variance power: {0}, true variance power: {1}".format(model_ml.actual_params["tweedie_variance_power"], trueDisp))
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.144064040064248) < 2e-4


def test_tweedie_var_power_estimation_2p6_disp2_est():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm_test/tweedie_p2p6_disp2_5Cols_10krows_est1p94.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    trueDisp = 2.6
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True, fix_tweedie_variance_power=False,
                                             tweedie_variance_power=trueDisp,
                                             lambda_=0, compute_p_values=False,
                                             init_dispersion_parameter=2.0,
                                             dispersion_parameter_method="ml", seed=12345)
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print("estimated variance power: {0}, true variance power: {1}".format(model_ml.actual_params["tweedie_variance_power"], trueDisp))
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.67437207518084) < 2e-4


def measure_time(t):
    def _():
        import time
        start = time.monotonic()
        t()
        print(f"The test {t.__name__} took {time.monotonic()-start}s")
    _.__name__ = t.__name__
    return _

def run_random_test():
    import random
    tests = [
        test_tweedie_var_power_estimation_2p1_disp2_est,  # takes 531s -> 49s (without Newton's method) 
        test_tweedie_var_power_estimation_2p6_disp2_est,  # takes 1034s -> 434s
        test_tweedie_var_power_estimation_2p5_phi_2p5_no_link_power_est,  # takes 1968s -> 1064s
        test_tweedie_var_power_estimation_3_phi_0p5_no_link_power_est,  # takes 1185s -> 685s
        test_tweedie_var_power_estimation_3_no_link_power_est,  # takes 2654s -> 1444s
        test_tweedie_var_power_estimation_3_phi_1p5_no_link_power_est,  # takes 2309s -> 1314s
        test_tweedie_var_power_estimation_5_no_link_power_est,  # takes 830s -> 964s
        test_tweedie_var_power_estimation_5_phi_0p5_no_link_power_est,  # takes 1151s -> 1221s
    ]
    return [random.choice(tests)]


pyunit_utils.run_tests(run_random_test())
