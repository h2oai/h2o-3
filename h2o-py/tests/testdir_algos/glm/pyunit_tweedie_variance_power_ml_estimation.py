import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def test_tweedie_var_power_estimation_3_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p3_phi1_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.995490728108107) < 0.001
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.995490728108107) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.990370178297177) < 0.001
    # assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.990370178297177) < 0.001


def test_tweedie_var_power_estimation_5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p5_phi1_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 5.015919757985777) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 5.015919757985777) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 5.015901230435018) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 5.015901230435018) < 0.001


def test_tweedie_var_power_estimation_5_phi_0p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p5_phi0p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=0.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 4.9526804187281765) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 4.9526804187281765) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=0.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 4.952682388241566) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 4.952682388241566) < 0.001


def test_tweedie_var_power_estimation_3_phi_1p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p3_phi1p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=1.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0043941544956168) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0172313489723956) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=1.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0005648582465207) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0172313489723956) < 0.001


def test_tweedie_var_power_estimation_3_phi_0p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p3_phi0p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=0.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.029819714027204) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0632864030533775) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=0.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.014297969690198) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0632864030533775) < 0.001


def test_tweedie_var_power_estimation_1p2_phi_2_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p1p2_phi2_5Cols_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=2,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.1956930002091224) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.2671241774599327) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=2,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.198411200545317) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 1.2671241774599327) < 0.001


def test_tweedie_var_power_estimation_2p5_phi_2p5_no_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p2p5_phi2p5_5Cols_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=2.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.565921555131599) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.5602696991543508) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=2.5,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.5657228160821295) < 0.001
    assert abs(model_ml.actual_params["tweedie_variance_power"] - 2.5602696991543508) < 0.001



def test_tweedie_var_power_estimation_1_8_no_link_power_est():
    training_data = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/tweedie_1p8Power_2Dispersion_5Col_10KRows.csv"))
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model_18 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.8, dispersion_learning_rate=1,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                             max_iterations_dispersion=1000)
    model_18.train(training_frame=training_data, x=x, y=Y)
    print("p = 1.8 converged to p =", model_18.actual_params["tweedie_variance_power"])
    assert abs(model_18.actual_params["tweedie_variance_power"] - 1.7985149491836738) < 1e-4
    model_11 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.1, dispersion_learning_rate=1,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                             max_iterations_dispersion=1000)
    model_11.train(training_frame=training_data, x=x, y=Y)
    print("p = 1.1 converged to p =", model_11.actual_params["tweedie_variance_power"])
    assert abs(model_11.actual_params["tweedie_variance_power"] - model_18.actual_params["tweedie_variance_power"]) < 1e-4

    model_201 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                              tweedie_variance_power=2.01, dispersion_learning_rate=1,
                                              fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                              dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                              max_iterations_dispersion=1000)
    model_201.train(training_frame=training_data, x=x, y=Y)
    print("p = 2.01 converged to p =", model_201.actual_params["tweedie_variance_power"])
    assert abs(model_11.actual_params["tweedie_variance_power"] - model_201.actual_params["tweedie_variance_power"]) < 1e-4

    model_21 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.1, dispersion_learning_rate=1,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                             max_iterations_dispersion=1000)
    model_21.train(training_frame=training_data, x=x, y=Y)
    print("p = 2.1 converged to p =", model_21.actual_params["tweedie_variance_power"])
    assert abs(model_11.actual_params["tweedie_variance_power"] - model_21.actual_params["tweedie_variance_power"]) < 1e-4

    model_25 = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5, dispersion_learning_rate=1,
                                             fix_tweedie_variance_power=True, lambda_=0, compute_p_values=True,
                                             dispersion_parameter_method="ml", init_dispersion_parameter=2,
                                             max_iterations_dispersion=1000)
    model_25.train(training_frame=training_data, x=x, y=Y)
    print("p = 2.5 converged to p =", model_25.actual_params["tweedie_variance_power"])
    assert abs(model_11.actual_params["tweedie_variance_power"] - model_25.actual_params["tweedie_variance_power"]) < 1e-4


def test_():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p3_phi1_10KRows.csv"))
    actual_p = 3
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    h2o.no_progress()
    for i in range(110, 600):
        p = i / 100.0
        try:
            model = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                                  tweedie_variance_power=p,
                                                  fix_tweedie_variance_power=True, lambda_=0, compute_p_values=False,
                                                  max_iterations=10000, dispersion_parameter_method="ml",
                                                  init_dispersion_parameter=1,
                                                  dispersion_learning_rate=1, max_iterations_dispersion=100,
                                                  remove_collinear_columns=True)
            model.train(training_frame=training_data, x=x, y=Y)
            print("p =", p, " converged to p =", model.actual_params["tweedie_variance_power"], "=>",
                  abs(model.actual_params["tweedie_variance_power"] - actual_p) < 0.05)
            #assert  abs(model.actual_params["tweedie_variance_power"] - actual_p) < 0.05
        except Exception as e:
            print("p =", p, "not converged due to an Error:", [l for l in str(e).splitlines() if "Exception" in l][0])


def test_tweedie_var_power_estimation_3_phi_0p5_with_link_power_est():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p3_phi0p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]

    p=3
    #training_data[Y] = training_data[Y]**(1-p) / (1-p)
    training_data[Y] = training_data[Y]**(1-p)

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=0.5,
                                             fix_tweedie_variance_power=False, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0632) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=0.5,
                                             fix_tweedie_variance_power=False, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0632) < 0.001


def test_tweedie_var_power_estimation_3_phi_0p5_with_link_power_est2():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/tweedie_p3_phi0p5_10KRows.csv"))
    Y = 'x'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    training_data = training_data[training_data[Y] > 0, :]
    p=3
    #training_data[Y] = training_data[Y]**(1/(1-p)) *  (1-p)
    training_data[Y] = training_data[Y]**(1/(1-p))

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=2.5, init_dispersion_parameter=0.5,
                                             fix_tweedie_variance_power=False, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0632) < 0.001

    model_ml = H2OGeneralizedLinearEstimator(family='tweedie', fix_dispersion_parameter=True,
                                             tweedie_variance_power=1.5, init_dispersion_parameter=0.5,
                                             fix_tweedie_variance_power=False, lambda_=0, compute_p_values=False,
                                             dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)
    print(model_ml.actual_params["tweedie_variance_power"])
    #assert abs(model_ml.actual_params["tweedie_variance_power"] - 3.0632) < 0.001


pyunit_utils.run_tests([
     # test_tweedie_var_power_estimation_3_phi_0p5_with_link_power_est,
     # test_tweedie_var_power_estimation_3_phi_0p5_with_link_power_est2,
    test_tweedie_var_power_estimation_5_phi_0p5_no_link_power_est,
    test_tweedie_var_power_estimation_1p2_phi_2_no_link_power_est,
    test_tweedie_var_power_estimation_1_8_no_link_power_est,
    test_tweedie_var_power_estimation_2p5_phi_2p5_no_link_power_est,
    test_tweedie_var_power_estimation_3_phi_0p5_no_link_power_est,
    test_tweedie_var_power_estimation_3_no_link_power_est,
    test_tweedie_var_power_estimation_3_phi_1p5_no_link_power_est,
    test_tweedie_var_power_estimation_5_no_link_power_est,
    # test_,
])
