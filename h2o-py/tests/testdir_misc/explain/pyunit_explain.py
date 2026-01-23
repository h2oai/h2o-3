import os
import sys

import pandas as pd

from h2o.grid import H2OGridSearch

sys.path.insert(1, os.path.join("..", "..", ".."))
import matplotlib
matplotlib.use("Agg")  # remove warning from python2 (missing TKinter)
import h2o
import pandas
import matplotlib.pyplot
from tests import pyunit_utils, test_plot_result_saving
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


def test_varimp():
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

    assert aml.varimp(use_pandas=True).shape == (12, 5)
    assert h2o.explanation.varimp(aml.leaderboard[aml.leaderboard["model_id"].grep("Stacked", invert=True, output_logical=True), :].head(3), num_of_features=3, use_pandas=True).shape == (3, 3)

    varimp_1 = aml.varimp(use_pandas=False)
    assert varimp_1[0].shape == (12, 5)
    assert len(varimp_1[1]) == 5
    assert len(varimp_1[2]) == 12

    varimp_2 = h2o.explanation.varimp(aml.leaderboard[aml.leaderboard["model_id"].grep("Stacked", invert=True, output_logical=True), :].head(4), num_of_features=3, use_pandas=False)
    assert varimp_2[0].shape == (3, 4)
    assert len(varimp_2[1]) == 4
    assert len(varimp_2[2]) == 3

    assert isinstance(aml.varimp_heatmap().figure(), matplotlib.pyplot.Figure)
    assert isinstance(h2o.varimp_heatmap(aml.leaderboard[aml.leaderboard["model_id"].grep("Stacked", invert=True, output_logical=True), :].head(3), num_of_features=3).figure(), matplotlib.pyplot.Figure)


def test_explanation_single_model_regression():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    y = "fare"

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
    assert isinstance(gbm.shap_summary_plot(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test shap explain row
    assert isinstance(gbm.shap_explain_row_plot(train, 1).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test residual analysis
    assert isinstance(gbm.residual_analysis_plot(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test pd_plot
    for col in cols_to_test:
        try:
            assert isinstance(gbm.pd_plot(train, col).figure(), matplotlib.pyplot.Figure)
        except ValueError:
            assert col == "name", "'name' is a string column which is not supported."

    # test ICE plot
    for col in cols_to_test:
        try:
            assert isinstance(gbm.ice_plot(train, col).figure(), matplotlib.pyplot.Figure)
        except ValueError:
            assert col == "name", "'name' is a string column which is not supported."
    matplotlib.pyplot.close("all")

    # test learning curve
    assert isinstance(gbm.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
    for metric in ["auto", "deviance", "rmse"]:
        assert isinstance(gbm.learning_curve_plot(metric=metric.upper()).figure(), matplotlib.pyplot.Figure)
        assert isinstance(gbm.learning_curve_plot(metric).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(gbm.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, render=False), H2OExplanation)


def test_explanation_automl_regression():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    train["name"] = train["name"].asfactor()
    y = "fare"

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
    assert isinstance(aml.varimp_heatmap().figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert len(aml.varimp(use_pandas=False)) == 3  # numpy.ndarray, colnames, rownames
    assert isinstance(aml.varimp(use_pandas=True), pandas.DataFrame)

    # test model correlation heatmap plot
    assert isinstance(aml.model_correlation_heatmap(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert len(aml.model_correlation(train, use_pandas=False)) == 2  # numpy.ndarray, colnames and rownames both in the same order => represented by just one vector
    assert isinstance(aml.model_correlation(train, use_pandas=True), pandas.DataFrame)


    # test partial dependences
    for col in cols_to_test:
        try:
            assert isinstance(aml.pd_multi_plot(train, col).figure(), matplotlib.pyplot.Figure)
        except ValueError:
            assert col == "name", "'name' is a string column which is not supported."
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(aml.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(aml.explain_row(train, 1, render=False), H2OExplanation)

    # test shortening model ids work correctly
    from h2o.explanation._explain import _shorten_model_ids
    model_ids = aml.leaderboard.as_data_frame()["model_id"]
    shortened_model_ids = _shorten_model_ids(model_ids)
    assert len(set(model_ids)) == len(set(shortened_model_ids))
    for i in range(len(model_ids)):
        assert len(model_ids[i]) > len(shortened_model_ids[i])

    # Leaderboard slices work
    # test explain
    assert isinstance(h2o.explain(aml.leaderboard[~aml.leaderboard["model_id"].grep("^Stacked", output_logical=True), :],
                                  train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(h2o.explain_row(aml.leaderboard[~aml.leaderboard["model_id"].grep("^Stacked", output_logical=True), :],
                                      train, 1, render=False), H2OExplanation)


def test_explanation_list_of_models_regression():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    y = "fare"

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
    assert isinstance(h2o.varimp_heatmap(models).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test model correlation heatmap plot
    assert isinstance(h2o.model_correlation_heatmap(models, train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test partial dependences
    for col in cols_to_test:
        try:
            assert isinstance(h2o.pd_multi_plot(models, train, col).figure(), matplotlib.pyplot.Figure)
        except ValueError:
            assert col == "name", "'name' is a string column which is not supported."
    matplotlib.pyplot.close("all")

    # test learning curve
    for model in models:
        assert isinstance(model.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
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
    assert isinstance(gbm.shap_summary_plot(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test shap explain row
    assert isinstance(gbm.shap_explain_row_plot(train, 1).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test pd_plot
    for col in cols_to_test:
        assert isinstance(gbm.pd_plot(train, col).figure(), matplotlib.pyplot.Figure)

    # test ICE plot
    for col in cols_to_test:
        assert isinstance(gbm.ice_plot(train, col).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test learning curve
    assert isinstance(gbm.learning_curve_plot().figure(), matplotlib.pyplot.Figure)

    # test explain
    assert isinstance(gbm.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, render=False), H2OExplanation)

    # test explain
    assert isinstance(gbm.explain(train, top_n_features=-1,  render=False), H2OExplanation)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, top_n_features=-1, render=False), H2OExplanation)


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
    assert isinstance(aml.varimp_heatmap().figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test that num_of_features is propagated
    for n_features in [1, 3, 5]:
        assert n_features == len(aml.varimp_heatmap(num_of_features=n_features).figure().get_axes()[0].get_yticks())
        matplotlib.pyplot.close()

    assert len(aml.varimp(use_pandas=False)) == 3  # numpy.ndarray, colnames, rownames
    assert isinstance(aml.varimp(use_pandas=True), pandas.DataFrame)

    # test model correlation heatmap plot
    assert isinstance(aml.model_correlation_heatmap(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert len(aml.model_correlation(train, use_pandas=False)) == 2  # numpy.ndarray, colnames and rownames both in the same order => represented by just one vector
    assert isinstance(aml.model_correlation(train, use_pandas=True), pandas.DataFrame)


    # test partial dependences
    for col in cols_to_test:
        assert isinstance(aml.pd_multi_plot(train, col).figure(), matplotlib.pyplot.Figure)
        matplotlib.pyplot.close()

    # test explain
    assert isinstance(aml.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(aml.explain_row(train, 1, render=False), H2OExplanation)

    # Leaderboard slices work
    # test variable importance heatmap plot
    assert isinstance(aml.varimp_heatmap().figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    leaderboard_without_SE = aml.leaderboard[~aml.leaderboard["model_id"].grep("^Stacked", output_logical=True), :]
    assert len(h2o.explanation.varimp(leaderboard_without_SE, use_pandas=False)) == 3  # numpy.ndarray, colnames, rownames
    assert isinstance(h2o.explanation.varimp(leaderboard_without_SE, use_pandas=True), pandas.DataFrame)

    # test model correlation heatmap plot
    assert isinstance(h2o.model_correlation_heatmap(leaderboard_without_SE, train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert len(h2o.explanation.model_correlation(leaderboard_without_SE, train, use_pandas=False)) == 2  # numpy.ndarray, colnames and rownames both in the same order => represented by just one vector
    assert isinstance(h2o.explanation.model_correlation(leaderboard_without_SE, train, use_pandas=True), pandas.DataFrame)

    # test partial dependences
    assert isinstance(h2o.pd_multi_plot(leaderboard_without_SE, train, cols_to_test[0]).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test explain
    assert isinstance(h2o.explain(leaderboard_without_SE, train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(h2o.explain_row(leaderboard_without_SE, train, 1, render=False), H2OExplanation)


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
    assert isinstance(h2o.varimp_heatmap(models).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test model correlation heatmap plot
    assert isinstance(h2o.model_correlation_heatmap(models, train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(h2o.pd_multi_plot(models, train, col).figure(), matplotlib.pyplot.Figure)
        matplotlib.pyplot.close()

    # test learning curve
    for model in models:
        assert isinstance(model.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
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
        assert isinstance(gbm.pd_plot(train, col, target="setosa").figure(), matplotlib.pyplot.Figure)

    # test ICE plot
    for col in cols_to_test:
        assert isinstance(gbm.ice_plot(train, col, target="setosa").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test learning curve
    assert isinstance(gbm.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

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
    assert isinstance(aml.varimp_heatmap().figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert len(aml.varimp(use_pandas=False)) == 3  # numpy.ndarray, colnames, rownames
    assert isinstance(aml.varimp(use_pandas=True), pandas.DataFrame)

    # test model correlation heatmap plot
    assert isinstance(aml.model_correlation_heatmap(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert len(aml.model_correlation(train, use_pandas=False)) == 2  # numpy.ndarray, colnames and rownames both in the same order => represented by just one vector
    assert isinstance(aml.model_correlation(train, use_pandas=True), pandas.DataFrame)

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(aml.pd_multi_plot(train, col, target="setosa").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(aml.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(aml.explain_row(train, 1, render=False), H2OExplanation)

    # Leaderboard slices work
    # test explain
    assert isinstance(h2o.explain(aml.leaderboard[~aml.leaderboard["model_id"].grep("^Stacked", output_logical=True), :], train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(h2o.explain_row(aml.leaderboard[~aml.leaderboard["model_id"].grep("^Stacked", output_logical=True), :], train, 1, render=False), H2OExplanation)


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
    assert isinstance(h2o.varimp_heatmap(models).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test model correlation heatmap plot
    assert isinstance(h2o.model_correlation_heatmap(models, train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(h2o.pd_multi_plot(models, train, col, target="setosa").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test learning curve
    for model in models:
        assert isinstance(model.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
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
    assert isinstance(glm_model.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
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
    assert isinstance(gam_model.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
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
    assert isinstance(glrm_model.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # CoxPH
    heart = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    coxph_model = H2OCoxProportionalHazardsEstimator(start_column="start",
                                                     stop_column="stop",
                                                     ties="breslow")
    coxph_model.train(x="age",
                      y="event",
                      training_frame=heart)
    assert isinstance(coxph_model.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert isinstance(coxph_model.learning_curve_plot(metric="loglik").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # IsolationForest
    if_model = H2OIsolationForestEstimator(sample_rate=0.1,
                                           max_depth=20,
                                           ntrees=50)
    if_model.train(training_frame=prostate)
    assert isinstance(if_model.learning_curve_plot().figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()


def test_explanation_timeseries():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/timeSeries/CreditCard-ts_train.csv"))
    x = ["MONTH", "LIMIT_BAL", "SEX", "EDUCATION", "MARRIAGE", "AGE", "PAY_STATUS", "PAY_AMT", "BILL_AMT"]
    y = "DEFAULT_PAYMENT_NEXT_MONTH"

    # Make sure it works with missing values as well
    train[[5, 7, 11, 13, 17], "MONTH"] = float("nan")

    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator()
    gbm.train(x, y, training_frame=train)

    # test shap summary
    assert isinstance(gbm.shap_summary_plot(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test shap explain row
    assert isinstance(gbm.shap_explain_row_plot(train, 1).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test residual analysis
    assert isinstance(gbm.residual_analysis_plot(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    # test pd_plot
    for col in cols_to_test:
        assert isinstance(gbm.pd_plot(train, col).figure(), matplotlib.pyplot.Figure)

    # test ICE plot
    for col in cols_to_test:
        assert isinstance(gbm.ice_plot(train, col).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    # test explain
    assert isinstance(gbm.explain(train, render=False), H2OExplanation)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, render=False), H2OExplanation)


def test_explanation_automl_pareto_front():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "CAPSULE"
    train[y] = train[y].asfactor()
    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    assert isinstance(aml.pareto_front(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert isinstance(aml.pareto_front(None, "mse", "rmse").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()


def test_explanation_grid_pareto_front():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "CAPSULE"
    train[y] = train[y].asfactor()
    gbm_params1 = {'learn_rate': [0.01, 0.1],
                   'max_depth': [3, 5, 9]}

    # Train and validate a cartesian grid of GBMs
    grid = H2OGridSearch(model=H2OGradientBoostingEstimator,
                         grid_id='gbm_grid1',
                         hyper_params=gbm_params1)
    grid.train(y=y, training_frame=train, seed=1)

    assert isinstance(grid.pareto_front(train).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert isinstance(grid.pareto_front(train, "mse", "rmse").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()


def test_explanation_some_dataframe_pareto_front():
    import pandas as pd
    df = pd.DataFrame({"A": [1, 2, 3, 4, 5], "b": [4, 1, 3, 5, 2], "c": [5, 4, 3, 2, 1]})
    h2o_df = h2o.H2OFrame(df)

    assert isinstance(h2o.explanation.pareto_front(df, "A", "c").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()

    assert isinstance(h2o.explanation.pareto_front(h2o_df, "A", "c").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close()


def test_pareto_front_corner_cases():
    df = pd.DataFrame(dict(
        name=("top left", "left", "left", "bottom left", "bottom", "bottom", "bottom right", "right", "right", "top right", "top", "top", "inner"),
        x   =(         0,      0,      0,             0,      0.3,      0.6,              1,       1,       1,           1,   0.7,   0.4,    0.5),
        y   =(         1,    0.8,    0.2,             0,        0,        0,              0,    0.35,    0.65,           1,     1,     1,    0.5)
    ))

    tl = h2o.explanation._explain._calculate_pareto_front(df["x"].values, df["y"].values, top=True, left=True)
    tr = h2o.explanation._explain._calculate_pareto_front(df["x"].values, df["y"].values, top=True, left=False)
    bl = h2o.explanation._explain._calculate_pareto_front(df["x"].values, df["y"].values, top=False, left=True)
    br = h2o.explanation._explain._calculate_pareto_front(df["x"].values, df["y"].values, top=False, left=False)

    assert tl.shape == (1,)
    assert tr.shape == (1,)
    assert bl.shape == (1,)
    assert br.shape == (1,)

    assert (df.loc[list(tl), "name"] == "top left").all()
    assert (df.loc[list(tr), "name"] == "top right").all()
    assert (df.loc[list(bl), "name"] == "bottom left").all()
    assert (df.loc[list(br), "name"] == "bottom right").all()

    df = pd.DataFrame(dict(
        name=("top left", "top left", "bottom left", "bottom left", "bottom left", "bottom right", "bottom right", "bottom right", "top right", "top right", "top right", "top left", "inner"),
        x   =(       0.1,          0,             0,           0.1,           0.3,      0.6,                  0.9,              1,           1,         0.9,         0.7,        0.4,    0.5),
        y   =(       0.9,        0.8,           0.2,           0.1,             0,        0,                  0.1,           0.35,        0.65,         0.9,           1,          1,    0.5)
    ))

    tl = h2o.explanation._explain._calculate_pareto_front(df["x"].values, df["y"].values, top=True, left=True)
    tr = h2o.explanation._explain._calculate_pareto_front(df["x"].values, df["y"].values, top=True, left=False)
    bl = h2o.explanation._explain._calculate_pareto_front(df["x"].values, df["y"].values, top=False, left=True)
    br = h2o.explanation._explain._calculate_pareto_front(df["x"].values, df["y"].values, top=False, left=False)

    assert tl.shape == (3,)
    assert tr.shape == (3,)
    assert bl.shape == (3,)
    assert br.shape == (3,)

    assert (df.loc[list(tl), "name"] == "top left").all()
    assert (df.loc[list(tr), "name"] == "top right").all()
    assert (df.loc[list(bl), "name"] == "bottom left").all()
    assert (df.loc[list(br), "name"] == "bottom right").all()

def test_fairness_plots():
    data = h2o.upload_file(pyunit_utils.locate("smalldata/admissibleml_test/taiwan_credit_card_uci.csv"))

    x = ['LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1', 'BILL_AMT2', 'BILL_AMT3',]
        # 'BILL_AMT4', 'BILL_AMT5', 'BILL_AMT6', 'PAY_AMT1', 'PAY_AMT2', 'PAY_AMT3', 'PAY_AMT4', 'PAY_AMT5', 'PAY_AMT6' ]
    y = "default payment next month"
    protected_columns = ['SEX', 'EDUCATION', 'MARRIAGE',]

    for c in [y]+protected_columns:
        data[c] = data[c].asfactor()

    train, test = data.split_frame([0.98], seed=123456)
    print(test.nrow)
    reference = ["1", "2", "2"]  # university educated single man
    favorable_class = "0"  # no default next month

    aml = H2OAutoML(max_models=12, seed=123456)
    aml.train(x, y, train)

    models = [h2o.get_model(m[0]) for m in aml.leaderboard["model_id"].as_data_frame(False, False)]
    da = h2o.explanation.disparate_analysis(models, test, protected_columns, reference, favorable_class)

    assert isinstance(h2o.explanation.pareto_front(da, "auc", "air_min", optimum="top right").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    assert isinstance(aml.get_best_model("deeplearning").inspect_model_fairness(test, protected_columns, reference, favorable_class, figsize=(6, 3)), H2OExplanation)
    matplotlib.pyplot.close("all")
    assert isinstance(aml.get_best_model("drf").inspect_model_fairness(test, protected_columns, reference, favorable_class, figsize=(6, 3)), H2OExplanation)
    matplotlib.pyplot.close("all")
    assert isinstance(aml.get_best_model("gbm").inspect_model_fairness(test, protected_columns, reference, favorable_class, figsize=(6, 3)), H2OExplanation)
    matplotlib.pyplot.close("all")
    assert isinstance(aml.get_best_model("glm").inspect_model_fairness(test, protected_columns, reference, favorable_class, figsize=(6, 3)), H2OExplanation)
    matplotlib.pyplot.close("all")
    assert isinstance(aml.get_best_model("xgboost").inspect_model_fairness(test, protected_columns, reference, favorable_class, figsize=(6, 3)), H2OExplanation)
    matplotlib.pyplot.close("all")


def test_pd_plot_row_value():
    import random
    import matplotlib.pyplot as plt
    def assert_row_value(fig, row_val, col):
        lines = [line for line in fig.axes[0].lines if len(line._y) == 2 and line.get_linestyle() == ":"]
        assert len(lines) == 1
        indicator_line = lines[0]
        if isinstance(row_val, float):
            print(col, "=>", indicator_line._x[0], "==", row_val)
            assert indicator_line._x[0] == row_val
        else:
            print(col, "=>", fig.axes[0].get_xticklabels()[int(indicator_line._x[0])].get_text(), "==", row_val)
            assert fig.axes[0].get_xticklabels()[int(indicator_line._x[0])].get_text() == row_val
        plt.close()

    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    train["name"] = train["name"].asfactor()
    y = "fare"

    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)

    for _ in range(20):
        i = random.randrange(train.nrows)
        print("\ntrain[" ,i, ", :]")
        assert_row_value(gbm.pd_plot(train, "name", row_index=i).figure(), train[i, "name"], "name")
        assert_row_value(gbm.pd_plot(train, "age", row_index=i).figure(), train[i, "age"], "age")

    i = 408  # Male
    print("\ntrain[" ,i, ", :]")
    assert_row_value(gbm.pd_plot(train, "sex", row_index=i).figure(), train[i, "sex"], "sex")

    i = 533  # Female
    print("\ntrain[" ,i, ", :]")
    assert_row_value(gbm.pd_plot(train, "sex", row_index=i).figure(), train[i, "sex"], "sex")


def test_shap_plots_with_background_frame():
    data = h2o.upload_file(pyunit_utils.locate("smalldata/admissibleml_test/taiwan_credit_card_uci.csv"))

    x = ['LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1', 'BILL_AMT2', 'BILL_AMT3',]
        # 'BILL_AMT4', 'BILL_AMT5', 'BILL_AMT6', 'PAY_AMT1', 'PAY_AMT2', 'PAY_AMT3', 'PAY_AMT4', 'PAY_AMT5', 'PAY_AMT6' ]
    y = "default payment next month"
    protected_columns = ['SEX', 'EDUCATION',]
    reference = ["1", "2"]
    favorable_class = "0"
    seed = 0xCAFFE

    for c in [y]+protected_columns:
        data[c] = data[c].asfactor()

    train, test = data[1:500,:].split_frame([0.98], seed=seed)
    print(test.nrow)
    

    aml = H2OAutoML(max_models=12, seed=seed)
    aml.train(x, y, train)

    models = []
    for algo in ["deeplearning", "drf", "gbm", "glm", "stackedensemble", "xgboost"]:
        print(algo)
        model = aml.get_best_model(algo)
        models.append(model)
        # test shap summary
        assert isinstance(model.shap_summary_plot(test, background_frame=train).figure(), matplotlib.pyplot.Figure)
        matplotlib.pyplot.close()

        # test shap explain row
        assert isinstance(model.shap_explain_row_plot(test, 1, background_frame=train).figure(), matplotlib.pyplot.Figure)
        matplotlib.pyplot.close()
    
        # test fair shap plot
        for p in model.fair_shap_plot(test, "AGE", protected_columns, figsize=(4, 3), background_frame=train).values():
            assert isinstance(p, matplotlib.pyplot.Figure)
        matplotlib.pyplot.close()

        mf =  model.inspect_model_fairness(test, protected_columns, reference, favorable_class, background_frame=train, render=False)
        assert len(mf["shap"]["plots"]) > 1
        matplotlib.pyplot.close("all")

    # Test that explain has the correct shap plots
    print("checking explain")
    ex = h2o.explain(models, test, render=False)
    exb = h2o.explain(models, test, background_frame=train, render=False)

    ex_shap_plots = ex["shap_summary"]["plots"]
    exb_shap_plots = exb["shap_summary"]["plots"]

    assert len(ex_shap_plots) < len(exb_shap_plots)

    assert not any("GLM" in k or "StackedEnsemble" in k or "DeepLearning" in k for k in ex_shap_plots.keys())
    assert any("GLM" in k or "StackedEnsemble" in k or "DeepLearning" in k for k in exb_shap_plots.keys())
    ex, exb, ex_shap_plots, exb_shap_plots = None, None, None, None
    matplotlib.pyplot.close("all")

    print("checking explain_row")
    ex = h2o.explain_row(models, test, 1, render=False)
    exb = h2o.explain_row(models, test, 1, background_frame=train, render=False)

    ex_shap_plots = ex["shap_explain_row"]["plots"]
    exb_shap_plots = exb["shap_explain_row"]["plots"]

    assert len(ex_shap_plots) < len(exb_shap_plots)

    assert not any("GLM" in k or "StackedEnsemble" in k or "DeepLearning" in k for k in ex_shap_plots.keys())
    assert any("GLM" in k or "StackedEnsemble" in k or "DeepLearning" in k for k in exb_shap_plots.keys())
    ex, exb, ex_shap_plots, exb_shap_plots = None, None, None, None
    matplotlib.pyplot.close("all")

    
def test_include_exclude_validation():
    from h2o.exceptions import H2OValueError
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    train["name"] = train["name"].asfactor()
    y = "fare"
    
    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model", ntrees=3)
    gbm.train(y=y, training_frame=train)

    try:
        gbm.explain(train, include_explanations=["lorem"])
        assert False, "Should fail as 'lorem' is not a valid explanation"
    except H2OValueError:
        pass

    try:
        gbm.explain(train, exclude_explanations=["lorem"])
        assert False, "Should fail as 'lorem' is not a valid explanation"
    except H2OValueError:
        pass

    assert isinstance(gbm.explain(train, include_explanations=["varimp"]), H2OExplanation)

    assert isinstance(gbm.explain(train, exclude_explanations=["pdp", "shap_summary", "ice", "residual_analysis"]), H2OExplanation)


pyunit_utils.run_tests([
    test_get_xy,
    test_varimp,
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
    test_explanation_timeseries,
    test_explanation_automl_pareto_front,
    test_explanation_grid_pareto_front,
    test_explanation_some_dataframe_pareto_front,
    test_pareto_front_corner_cases,
    test_pd_plot_row_value,
    test_fairness_plots,
    test_shap_plots_with_background_frame,
    test_include_exclude_validation,
    ])
