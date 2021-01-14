from __future__ import print_function
import sys, os, collections

sys.path.insert(1, os.path.join("..", "..", ".."))
import matplotlib
matplotlib.use("Agg")  # remove warning from python2 (missing TKinter)
import h2o
import matplotlib.pyplot
from tests import pyunit_utils
from h2o.automl import H2OAutoML
from h2o.estimators import *
from h2o.explanation._explain import H2OExplanation


def test_get_xy():
    import h2o.explanation._explain as ex
    train = h2o.upload_file(pyunit_utils.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    x = ["citric acid", "residual sugar", "chlorides", "free sulfur dioxide", "total sulfur dioxide", "density", "pH"]
    gbm = h2o.estimators.H2OGradientBoostingEstimator()
    gbm.train(x=x, y=y, training_frame=train, offset_column="sulphates", weights_column="alcohol", fold_column="type")

    estimated_x, estimated_y = ex._get_xy(gbm)
    assert set(x) == set(estimated_x)
    assert y == estimated_y

    # test it works also without any "special" column specified
    gbm2 = h2o.estimators.H2OGradientBoostingEstimator()
    gbm2.train(x=x, y=y, training_frame=train)

    estimated_x, estimated_y = ex._get_xy(gbm2)
    assert set(x) == set(estimated_x)
    assert y == estimated_y


def test_explanation_single_model_regression():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)

    # test shap summary
    assert isinstance(gbm.shap_summary_plot(train), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test shap explain row
    assert isinstance(gbm.shap_explain_row_plot(train, 1), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test residual analysis
    assert isinstance(gbm.residual_analysis_plot(train), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test pd_plot
    for col in cols_to_test:
        assert isinstance(gbm.pd_plot(train, col), matplotlib.pyplot.Figure)

    # test ICE plot
    for col in cols_to_test:
        assert isinstance(gbm.ice_plot(train, col), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test learning curve
    assert isinstance(gbm.learning_curve_plot(), matplotlib.pyplot.Figure)
    for metric in ["auto", "deviance", "rmse"]:
        assert isinstance(gbm.learning_curve_plot(metric=metric.upper()), matplotlib.pyplot.Figure)
        assert isinstance(gbm.learning_curve_plot(metric), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(gbm.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, render=False), H2OExplanation)


def test_explanation_automl_regression():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    # test variable importance heatmap plot
    assert isinstance(aml.varimp_heatmap(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test model correlation heatmap plot
    assert isinstance(aml.model_correlation_heatmap(train), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(aml.pd_multi_plot(train, col), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(aml.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(aml.explain_row(train, 1, render=False), H2OExplanation)


def test_explanation_list_of_models_regression():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    models = [h2o.get_model(m[0]) for m in
              aml.leaderboard["model_id"].as_data_frame(use_pandas=False, header=False)]

    # Test named models as well
    gbm = H2OGradientBoostingEstimator(model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)
    models += [gbm]

    # test variable importance heatmap plot
    assert isinstance(h2o.varimp_heatmap(models), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test model correlation heatmap plot
    assert isinstance(h2o.model_correlation_heatmap(models, train), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(h2o.pd_multi_plot(models, train, col), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test learning curve
    for model in models:
        assert isinstance(model.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(h2o.explain(models, train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(h2o.explain_row(models, train, 1, render=False), H2OExplanation)


def test_explanation_single_model_binomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "CAPSULE"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)

    # test shap summary
    assert isinstance(gbm.shap_summary_plot(train), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test shap explain row
    assert isinstance(gbm.shap_explain_row_plot(train, 1), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test pd_plot
    for col in cols_to_test:
        assert isinstance(gbm.pd_plot(train, col), matplotlib.pyplot.Figure)

    # test ICE plot
    for col in cols_to_test:
        assert isinstance(gbm.ice_plot(train, col), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test learning curve
    assert isinstance(gbm.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test explain
    assert isinstance(gbm.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, render=False), H2OExplanation)


def test_explanation_automl_binomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "CAPSULE"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    # test variable importance heatmap plot
    assert isinstance(aml.varimp_heatmap(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test model correlation heatmap plot
    assert isinstance(aml.model_correlation_heatmap(train), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(aml.pd_multi_plot(train, col), matplotlib.pyplot.Figure)
        matplotlib.pyplot.close()

    # test explain
    assert isinstance(aml.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(aml.explain_row(train, 1, render=False), H2OExplanation)


def test_explanation_list_of_models_binomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "CAPSULE"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    models = [h2o.get_model(m[0]) for m in
              aml.leaderboard["model_id"].as_data_frame(use_pandas=False, header=False)]

    # Test named models as well
    gbm = H2OGradientBoostingEstimator(model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)
    models += [gbm]

    # test variable importance heatmap plot
    assert isinstance(h2o.varimp_heatmap(models), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test model correlation heatmap plot
    assert isinstance(h2o.model_correlation_heatmap(models, train), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(h2o.pd_multi_plot(models, train, col), matplotlib.pyplot.Figure)
        matplotlib.pyplot.close()

    # test learning curve
    for model in models:
        assert isinstance(model.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(h2o.explain(models, train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(h2o.explain_row(models, train, 1, render=False), H2OExplanation)


def test_explanation_single_model_multinomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris2.csv"))
    y = "response"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)

    # test shap summary
    try:
        gbm.shap_summary_plot(train)
        matplotlib.pyplot.close()
        assert False, "SHAP Contributions aren't implemented for multinomial classification => should fail"
    except EnvironmentError:
        pass

    # test shap explain row
    try:
        gbm.shap_explain_row_plot(train, 1)
        matplotlib.pyplot.close()
        assert False, "SHAP Contributions aren't implemented for multinomial classification => should fail"
    except EnvironmentError:
        pass

    # test pd_plot
    for col in cols_to_test:
        assert isinstance(gbm.pd_plot(train, col, target="setosa"), matplotlib.pyplot.Figure)

    # test ICE plot
    for col in cols_to_test:
        assert isinstance(gbm.ice_plot(train, col, target="setosa"), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test learning curve
    assert isinstance(gbm.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test explain
    assert isinstance(gbm.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, render=False), H2OExplanation)


def test_explanation_automl_multinomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris2.csv"))
    y = "response"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    # test variable importance heatmap plot
    assert isinstance(aml.varimp_heatmap(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test model correlation heatmap plot
    assert isinstance(aml.model_correlation_heatmap(train), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(aml.pd_multi_plot(train, col, target="setosa"), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(aml.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(aml.explain_row(train, 1, render=False), H2OExplanation)


def test_explanation_list_of_models_multinomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris2.csv"))
    y = "response"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    models = [h2o.get_model(m[0]) for m in
              aml.leaderboard["model_id"].as_data_frame(use_pandas=False, header=False)]

    # Test named models as well
    gbm = H2OGradientBoostingEstimator(model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)
    models += [gbm]

    # test variable importance heatmap plot
    assert isinstance(h2o.varimp_heatmap(models), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test model correlation heatmap plot
    assert isinstance(h2o.model_correlation_heatmap(models, train), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(h2o.pd_multi_plot(models, train, col, target="setosa"), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test learning curve
    for model in models:
        assert isinstance(model.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(h2o.explain(models, train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(h2o.explain_row(models, train, 1, render=False), H2OExplanation)


def test_learning_curve_for_algos_not_present_in_automl():
    # GLM without lambda search
    prostate = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
    prostate['RACE'] = prostate['RACE'].asfactor()
    prostate['DCAPS'] = prostate['DCAPS'].asfactor()
    prostate['DPROS'] = prostate['DPROS'].asfactor()

    predictors = ["AGE", "RACE", "VOL", "GLEASON"]
    response_col = "CAPSULE"

    glm_model = H2OGeneralizedLinearEstimator(family="binomial",
                                              lambda_=0,
                                              compute_p_values=True)
    glm_model.train(predictors, response_col, training_frame=prostate)
    assert isinstance(glm_model.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # HGLM
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/semiconductor.csv"))
    y = "y"
    x = ["x1", "x3", "x5", "x6"]
    z = 0
    h2o_data["Device"] = h2o_data["Device"].asfactor()
    hglm_model = H2OGeneralizedLinearEstimator(HGLM=True,
                                               family="gaussian",
                                               rand_family=["gaussian"],
                                               random_columns=[z],
                                               rand_link=["identity"],
                                               calc_like=True)
    hglm_model.train(x=x, y=y, training_frame=h2o_data)
    assert isinstance(hglm_model.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # GAM
    knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
    frameKnots1 = h2o.H2OFrame(python_obj=knots1)
    knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
    frameKnots2 = h2o.H2OFrame(python_obj=knots2)
    knots3 = [-1.999675688, -0.979893796, 0.007573327, 1.011437347, 1.999611676]
    frameKnots3 = h2o.H2OFrame(python_obj=knots3)
    h2o_data = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    train, test = h2o_data.split_frame(ratios=[.8])
    y = "C11"
    x = ["C1", "C2"]
    numKnots = [5, 5, 5]
    gam_model = H2OGeneralizedAdditiveEstimator(family='multinomial',
                                                gam_columns=["C6", "C7", "C8"],
                                                scale=[1, 1, 1],
                                                num_knots=numKnots,
                                                knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key])
    gam_model.train(x=x, y=y, training_frame=train, validation_frame=test)
    assert isinstance(gam_model.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # GLRM
    arrestsH2O = h2o.import_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
    glrm_model = H2OGeneralizedLowRankEstimator(k=4,
                                                loss="quadratic",
                                                gamma_x=0.5,
                                                gamma_y=0.5,
                                                max_iterations=700,
                                                recover_svd=True,
                                                init="SVD",
                                                transform="standardize")
    glrm_model.train(training_frame=arrestsH2O)
    assert isinstance(glrm_model.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # CoxPH
    heart = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    coxph_model = H2OCoxProportionalHazardsEstimator(start_column="start",
                                                     stop_column="stop",
                                                     ties="breslow")
    coxph_model.train(x="age",
                      y="event",
                      training_frame=heart)
    assert isinstance(coxph_model.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # IsolationForest
    if_model = H2OIsolationForestEstimator(sample_rate=0.1,
                                           max_depth=20,
                                           ntrees=50)
    if_model.train(training_frame=prostate)
    assert isinstance(if_model.learning_curve_plot(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()


pyunit_utils.run_tests([
    test_get_xy,
    test_explanation_single_model_regression,
    test_explanation_automl_regression,
    test_explanation_list_of_models_regression,
    test_explanation_single_model_binomial_classification,
    test_explanation_automl_binomial_classification,
    test_explanation_list_of_models_binomial_classification,
    test_explanation_single_model_multinomial_classification,
    test_explanation_automl_multinomial_classification,
    test_explanation_list_of_models_multinomial_classification,
    test_learning_curve_for_algos_not_present_in_automl,
    ])
