import os
import sys
sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils, test_plot_result_saving
from h2o.automl import H2OAutoML
from h2o.estimators import *
from h2o.grid import *


def test_train_subset_models():
    x = ["loan_amount", "loan_to_value_ratio", "no_intro_rate_period", "intro_rate_period",
         "property_value", "income", "debt_to_income_ratio", "term_360", "conforming"]

    y = "high_priced"

    train = h2o.import_file("https://raw.githubusercontent.com/h2oai/article-information-2019/master/data/output/hmda_train.csv")
    test = h2o.import_file("https://raw.githubusercontent.com/h2oai/article-information-2019/master/data/output/hmda_test.csv")
    train = train[~train["black"].isna()[0] & ~train["hispanic"].isna()[0] & ~train["male"].isna()[0] & ~train["agegte62"].isna()[0],:]
    test = test[~test["black"].isna()[0] & ~test["hispanic"].isna()[0] & ~test["male"].isna()[0] & ~test["agegte62"].isna()[0],:]
    protected_columns = ["ethnic", "sex"]
    reference = ["white", "M"]
    favorable_class = "0"
    for d in [train, test]:
        d[:, y] = d[:, y].asfactor()
        d[d["black"] == 1, "ethnic"] = "black"
        d[d["asian"] == 1, "ethnic"] = "asian"
        d[d["white"] == 1, "ethnic"] = "white"
        d[d["amind"] == 1, "ethnic"] = "amind"
        d[d["hipac"] == 1, "ethnic"] = "hipac"
        d[d["hispanic"] == 1, "ethnic"] = "hispanic"
        d["sex"] = "NA"
        d[d["female"] == 1, "sex"] = "F"
        d[d["male"] == 1, "sex"] = "M"
        d["ethnic"] = d["ethnic"].asfactor()
        d["sex"] = d["sex"].asfactor()

    ig = H2OInfogram(protected_columns=["ethnic", "sex"])
    ig.train(x, y, training_frame=train)

    # GBM
    da = ig.train_subset_models(H2OGradientBoostingEstimator, y, train, test, protected_columns, reference, favorable_class)
    assert da.nrows == 9
    assert (da[:, "air_min"] > 0.7).all()
    assert (da[:, "air_max"] < 1.2).all()
    assert ((da[:, "air_min"] > 0.8) & (da[:, "air_max"] < 1.25)).any()  # four-fifths rule
    assert ((da[:, "cair"] > 0.8) & (da[:, "cair"] < 1.25)).any()  # four-fifths rule

    # AutoML
    da = ig.train_subset_models(H2OAutoML,  y, train, test, protected_columns, reference, favorable_class, max_models=2)
    assert da.nrows == 2 * 9 + 9  # models + SEs
    # some SEs tend to be more unfair than base models, so I relaxed the condition here
    assert ((da[:, "air_min"] > 0.8) & (da[:, "air_max"] < 1.25)).any()  # four-fifths rule
    assert ((da[:, "cair"] > 0.8) & (da[:, "cair"] < 1.25)).any()  # four-fifths rule

    # GRID
    da = ig.train_subset_models(H2OGridSearch, y, train, test, protected_columns, reference, favorable_class, model=H2OGradientBoostingEstimator(),
                        hyper_params=dict(ntrees=[1, 3, 5]))
    assert da.nrows == 3 * 9
    assert (da[:, "air_min"] > 0.7).all()
    assert (da[:, "air_max"] < 1.2).all()
    assert ((da[:, "air_min"] > 0.8) & (da[:, "air_max"] < 1.25)).any()  # four-fifths rule
    assert ((da[:, "cair"] > 0.8) & (da[:, "cair"] < 1.25)).any()  # four-fifths rule


def test_train_subset_models_taiwan():
    data = h2o.import_file(pyunit_utils.locate("smalldata/admissibleml_test/taiwan_credit_card_uci.csv"))
    x = ['LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1', 'BILL_AMT2', 'BILL_AMT3',
         'BILL_AMT4', 'BILL_AMT5', 'BILL_AMT6', 'PAY_AMT1', 'PAY_AMT2', 'PAY_AMT3', 'PAY_AMT4', 'PAY_AMT5', 'PAY_AMT6']
    y = "default payment next month"
    protected_columns = ['SEX', 'EDUCATION', 'MARRIAGE',]

    for c in [y]+protected_columns:
        data[c] = data[c].asfactor()

    train, test = data.split_frame([0.8])

    reference = ["1", "2", "2"]  # university educated single man
    favorable_class = "0"  # no default next month

    ig = H2OInfogram(protected_columns=protected_columns)
    ig.train(x, y, training_frame=train)

    # GBM
    da = ig.train_subset_models(H2OGradientBoostingEstimator, y, train, test, protected_columns, reference, favorable_class)
    assert da.nrows == len(x)
    assert ((da[:, "cair"] > 0.8) & (da[:, "cair"] < 1.25)).any()  # four-fifths rule

    # AutoML
    da = ig.train_subset_models(H2OAutoML, y, train, test, protected_columns, reference, favorable_class, max_models=2)
    assert da.nrows == 2 * len(x) + len(x)  # models + SEs
    assert ((da[:, "cair"] > 0.8) & (da[:, "cair"] < 1.25)).any()  # four-fifths rule

    # GRID
    da = ig.train_subset_models(H2OGridSearch, y, train, test, protected_columns, reference, favorable_class, model=H2OGradientBoostingEstimator(),
                        hyper_params=dict(ntrees=[1, 3, 5]))
    assert da.nrows == 3 * len(x)
    assert ((da[:, "cair"] > 0.8) & (da[:, "cair"] < 1.25)).any()  # four-fifths rule


def test_train_subset_models_metrics():
    data = h2o.import_file(pyunit_utils.locate("smalldata/admissibleml_test/taiwan_credit_card_uci.csv"))
    x = ['LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1', 'BILL_AMT2', 'BILL_AMT3',
         'BILL_AMT4', 'BILL_AMT5', 'BILL_AMT6', 'PAY_AMT1', 'PAY_AMT2', 'PAY_AMT3', 'PAY_AMT4', 'PAY_AMT5', 'PAY_AMT6']
    y = "default payment next month"
    protected_columns = ['SEX', 'EDUCATION', 'MARRIAGE',]

    for c in [y]+protected_columns:
        data[c] = data[c].asfactor()

    train, test = data.split_frame([0.8])

    reference = ["1", "2", "2"]  # university educated single man
    favorable_class = "0"  # no default next month

    ig_fair = H2OInfogram(protected_columns=protected_columns)
    ig_fair.train(x, y, training_frame=train)

    ig_core = H2OInfogram()
    ig_core.train(x, y, training_frame=train)

    # Fair, safety_index
    da = ig_fair.train_subset_models(H2OGradientBoostingEstimator, y, train, test, protected_columns, reference, favorable_class)
    assert da.nrows == len(x)

    # Fair, safety_index + admissible_index
    da = ig_fair.train_subset_models(H2OGradientBoostingEstimator, y, train, test, protected_columns, reference, favorable_class,
                                      feature_selection_metrics=["safety_index", "admissible_index"])
    assert da.nrows == len(x)

    # Fair, safety_index, manhattan distance
    da = ig_fair.train_subset_models(H2OGradientBoostingEstimator, y, train, test, protected_columns, reference, favorable_class, metric="manhattan")
    assert da.nrows == len(x)

    # Fair, safety_index + admissible_index, manhattan distance
    da = ig_fair.train_subset_models(H2OGradientBoostingEstimator, y, train, test, protected_columns, reference, favorable_class,
                                      feature_selection_metrics=["safety_index", "admissible_index"], metric="manhattan")
    assert da.nrows == len(x)

    # Fair, safety_index, maximum distance
    da = ig_fair.train_subset_models(H2OGradientBoostingEstimator, y, train, test, protected_columns, reference, favorable_class, metric="maximum")
    assert da.nrows == len(x)

    # Fair, safety_index + admissible_index, maximum distance
    da = ig_fair.train_subset_models(H2OGradientBoostingEstimator, y, train, test, protected_columns, reference, favorable_class,
                                      feature_selection_metrics=["safety_index", "admissible_index"], metric="maximum")
    assert da.nrows == len(x)

    # Core, admissible_index
    da = ig_core.train_subset_models(H2OGradientBoostingEstimator, y, train, test)
    assert da.nrows == len(x)

    # Core, total_information + admissible_index
    da = ig_core.train_subset_models(H2OGradientBoostingEstimator,y, train, test,
                                      feature_selection_metrics=["total_information", "admissible_index"])
    assert da.nrows == len(x)

    # Core, admissible_index, manhattan distance
    da = ig_core.train_subset_models(H2OGradientBoostingEstimator, y, train, test, metric="manhattan")
    assert da.nrows == len(x)

    # Core, total_information + admissible_index, manhattan distance
    da = ig_core.train_subset_models(H2OGradientBoostingEstimator, y, train, test,
                                      feature_selection_metrics=["total_information", "admissible_index"], metric="manhattan")
    assert da.nrows == len(x)

    # Core, admissible_index, maximum distance
    da = ig_core.train_subset_models(H2OGradientBoostingEstimator, y, train, test, metric="maximum")
    assert da.nrows == len(x)

    # Core, total_information + admissible_index, maximum distance
    da = ig_core.train_subset_models(H2OGradientBoostingEstimator, y, train, test,
                                      feature_selection_metrics=["total_information", "admissible_index"], metric="maximum")
    assert da.nrows == len(x)


pyunit_utils.run_tests([
 #  test_train_subset_models,  # uses data outside of smalldata; useful for debugging
    test_train_subset_models_taiwan,
    test_train_subset_models_metrics,
])
