from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import re
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML


def get_partitioned_model_names(leaderboard):
    model_names = [leaderboard[i, 0] for i in range(0, (leaderboard.nrows))]
    se_model_names = [m for m in model_names if m.startswith('StackedEnsemble')]
    non_se_model_names = [m for m in model_names if m not in se_model_names]
    return model_names, non_se_model_names, se_model_names


def check_model_property(model_names, prop_name, present=True, actual_value=None, default_value=None):
    for mn in model_names:
        model = h2o.get_model(mn)
        if present:
            assert prop_name in model.params.keys(), \
                "missing {prop} in model {model}".format(prop=prop_name, model=mn)
            assert actual_value is None or model.params[prop_name]['actual'] == actual_value, \
                "actual value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=mn, val=model.params[prop_name]['actual'], exp=actual_value)
            assert default_value is None or model.params[prop_name]['default'] == default_value, \
                "default value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=mn, val=model.params[prop_name]['default'], exp=default_value)
        else:
            assert prop_name not in model.params.keys(), "unexpected {prop} in model {model}".format(prop=prop_name, model=mn)


def list_keys_in_memory():
    mem_keys = h2o.ls().key
    automl_keys = [k for k in mem_keys if re.search('_AutoML_', k)]
    pred_keys = [k for k in mem_keys if re.search('(^|_)prediction_', k)]
    metrics_keys = [k for k in mem_keys if re.search('^modelmetrics_', k)]
    model_keys = [k for k in automl_keys if k not in pred_keys and k not in metrics_keys]
    cv_keys = [k for k in mem_keys if re.search('(^|_)cv_', k)]
    cv_pred_keys = [k for k in cv_keys if k in pred_keys]
    cv_metrics_keys = [k for k in cv_keys if k in metrics_keys]
    cv_mod_keys = [k for k in cv_keys if k in model_keys]
    return dict(
        total=mem_keys,
        models = model_keys,
        predictions = pred_keys,
        metrics = metrics_keys,
        automl = automl_keys,
        cv_all=cv_keys,
        cv_models=cv_mod_keys,
        cv_predictions=cv_pred_keys,
        cv_metrics=cv_metrics_keys
    )


def prepare_data():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    fr = df.split_frame(ratios=[.8,.1], seed=1)
    train, valid, test = fr[0], fr[1], fr[2]

    train['CAPSULE'] = train['CAPSULE'].asfactor()
    valid['CAPSULE'] = valid['CAPSULE'].asfactor()
    test['CAPSULE'] = test['CAPSULE'].asfactor()
    return train, valid, test


def test_clean_cv_predictions():
    kcvp = 'keep_cross_validation_predictions'
    nfolds = 5

    print("\n=== "+kcvp+" default behaviour ===")
    h2o.remove_all()
    train, _, _ = prepare_data()
    aml = H2OAutoML(project_name='keep_cross_validation_predictions_default',
                    nfolds=nfolds, max_models=3, seed=1)
    aml.train(y='CAPSULE', training_frame=train)
    print(aml.leaderboard)
    keys = list_keys_in_memory()
    preds = len(keys['cv_predictions'])
    assert preds == 0, "{preds} CV predictions were not cleaned from memory".format(preds=preds)

    print("\n=== enabling "+kcvp+" ===")
    h2o.remove_all()
    train, _, _ = prepare_data()
    aml = H2OAutoML(project_name='keep_cross_validation_predictions_default',
                    nfolds=nfolds, max_models=3, seed=1,
                    keep_cross_validation_predictions=True)
    aml.train(y='CAPSULE', training_frame=train)
    print(aml.leaderboard)
    _, non_se, _ = get_partitioned_model_names(aml.leaderboard)
    keys = list_keys_in_memory()
    preds = len(keys['cv_predictions'])
    expected = len(non_se) * (nfolds + 1) # +1 for holdout prediction
    assert preds == expected, "missing CV predictions in memory, got {actual}, expected {expected}".format(actual=preds, expected=expected)

    print("\n=== disabling "+kcvp+" ===")
    h2o.remove_all()
    train, _, _ = prepare_data()
    aml = H2OAutoML(project_name='keep_cross_validation_predictions_default',
                    nfolds=nfolds, max_models=3, seed=1,
                    keep_cross_validation_predictions=False)
    aml.train(y='CAPSULE', training_frame=train)
    print(aml.leaderboard)
    keys = list_keys_in_memory()
    preds = len(keys['cv_predictions'])
    assert preds == 0, "{preds} CV predictions were not cleaned from memory".format(preds=preds)


def test_clean_cv_models():
    kcvm = 'keep_cross_validation_models'
    nfolds = 5

    print("\n=== "+kcvm+" default behaviour ===")
    h2o.remove_all()
    train, _, _ = prepare_data()
    aml = H2OAutoML(project_name='keep_cross_validation_models_default',
                    nfolds=nfolds, max_models=3, seed=1)
    aml.train(y='CAPSULE', training_frame=train)
    print(aml.leaderboard)

    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    check_model_property(se, kcvm, False)
    check_model_property(non_se, kcvm, True, False, False)
    keys = list_keys_in_memory()
    tot, cv = len(keys['models']), len(keys['cv_models'])
    print("total models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
    assert tot > 0, "no models left in memory"
    assert cv == 0, "{cv} CV models were not cleaned from memory".format(cv=cv)


    print("\n=== enabling "+kcvm+" ===")
    h2o.remove_all()
    train, _, _ = prepare_data()
    aml = H2OAutoML(project_name='keep_cross_validation_models_enabled',
                    nfolds=nfolds,  max_models=8, seed=1,
                    keep_cross_validation_models=True)
    aml.train(y='CAPSULE', training_frame=train)
    print(aml.leaderboard)

    models, non_se, se = get_partitioned_model_names(aml.leaderboard)
    check_model_property(se, kcvm, False)
    check_model_property(non_se, kcvm, True, True, False)
    keys = list_keys_in_memory()
    tot, cv = len(keys['models']), len(keys['cv_models'])
    print("total models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
    assert tot > 0, "no models left in memory"
    expected = len(models) * nfolds
    assert cv == expected, "missing CV models in memory, got {actual}, expected {expected}".format(actual=cv, expected=expected)


    print("\n=== disabling "+kcvm+" ===")
    h2o.remove_all()
    train, _, _ = prepare_data()
    aml = H2OAutoML(project_name='keep_cross_validation_models_disabled',
                    nfolds=nfolds, max_models=8, seed=1,
                    keep_cross_validation_models=False)
    aml.train(y='CAPSULE', training_frame=train)
    print(aml.leaderboard)

    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    check_model_property(se, kcvm, False)
    check_model_property(non_se, kcvm, True, False, False)
    keys = list_keys_in_memory()
    tot, cv = len(keys['models']), len(keys['cv_models'])
    print("total models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
    assert tot > 0, "no models left in memory"
    assert cv == 0, "{cv} CV models were not cleaned from memory".format(cv=cv)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_clean_cv_predictions)
    pyunit_utils.standalone_test(test_clean_cv_models)
else:
    test_clean_cv_predictions()
    test_clean_cv_models()
