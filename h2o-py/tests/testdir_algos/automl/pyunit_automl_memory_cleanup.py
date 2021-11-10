from __future__ import print_function
import sys, os
import re

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o import H2OFrame
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset, get_partitioned_model_names


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


def list_keys_in_memory(project_name=None):
    mem_keys = h2o.ls().key
    automl_keys = [k for k in mem_keys if re.search(r'_AutoML_', k) and (project_name is None or project_name not in k)]
    automl_frame_keys = [k for k in mem_keys if re.search(r'^levelone_', k)]
    prediction_keys = [k for k in mem_keys if re.search(r'(^|_)prediction_', k)]
    metrics_keys = [k for k in mem_keys if re.search(r'^modelmetrics_', k)]
    metalearner_keys = [k for k in mem_keys if re.search(r'^metalearner', k)]
    fold_keys = [k for k in mem_keys if re.search(r'_fold_', k)]
    all_model_keys = [k for k in automl_keys
                      if k not in automl_frame_keys
                      and k not in prediction_keys
                      and k not in metrics_keys
                      and k not in fold_keys]
    cv_keys = [k for k in mem_keys if re.search(r'(^|_)cv_', k)]
    cv_prediction_keys = [k for k in cv_keys if k in prediction_keys]
    cv_metrics_keys = [k for k in cv_keys if k in metrics_keys]
    cv_fold_assignment = [k for k in cv_keys if k in fold_keys]
    cv_model_keys = [k for k in cv_keys
                     if k in all_model_keys
                     and k not in cv_fold_assignment]
    base_model_keys = [k for k in all_model_keys
                       if k not in cv_keys
                       and k not in metalearner_keys]
    return dict(
        all=mem_keys,
        models_all=all_model_keys,
        models_base=base_model_keys,
        predictions=prediction_keys,
        metrics=metrics_keys,
        automl=automl_keys,
        cv_all=cv_keys,
        cv_models=cv_model_keys,
        cv_predictions=cv_prediction_keys,
        cv_metrics=cv_metrics_keys,
        cv_fold_assignment=cv_fold_assignment,
        metalearners=metalearner_keys,
    )


def test_suite_clean_cv_predictions():
    kcvp = 'keep_cross_validation_predictions'
    nfolds = 5

    def setup_and_train(param_enabled=None):
        h2o.remove_all()
        ds = import_dataset()
        state = 'enabled' if param_enabled is True else 'disabled' if param_enabled is False else 'default'
        if param_enabled is None:
            aml = H2OAutoML(project_name='keep_cross_validation_predictions_'+state,
                            nfolds=nfolds, max_models=3, seed=1)
        else:
            aml = H2OAutoML(project_name='keep_cross_validation_predictions_'+state,
                            nfolds=nfolds, max_models=8, seed=1,
                            keep_cross_validation_predictions=param_enabled)

        aml.train(y=ds.target, training_frame=ds.train)
        # print(aml.leaderboard)
        return aml

    def assert_cv_predictions_on_model(model_name, present=True):
        model = h2o.get_model(model_name)
        cv_predictions = model.cross_validation_predictions()
        holdout_predictions = model.cross_validation_holdout_predictions()

        # see last comment line below for ideal assertion if cv predictions could be returned as null,
        #  but this can't be done in a clean way for autoML right now due to StackedEnsemble
        # assert not h2o.get_model(m).cross_validation_predictions(), "unexpected cv predictions for model "+m
        for p in cv_predictions:
            if present:
                assert p is not None, "missing cv predictions for model "+model_name
            else:
                assert not p, "unexpected cv predictions for model "+model_name

        if present:
            assert holdout_predictions is not None, "missing holdout predictions for model "+model_name
        else:
            assert not holdout_predictions, "unexpected holdout predictions for model "+model_name

    def test_default_behaviour():
        print("\n=== "+kcvp+" default behaviour ===")
        aml = setup_and_train()
        models = get_partitioned_model_names(aml.leaderboard)
        keys = list_keys_in_memory()
        preds = len(keys['cv_predictions'])
        assert preds == 0, "{preds} CV predictions were not cleaned from memory".format(preds=preds)
        for m in models.base:
            assert_cv_predictions_on_model(m, False)
        for m in models.se:
            assert not h2o.get_model(h2o.get_model(m).metalearner()['name']).cross_validation_predictions()

    def test_param_enabled():
        print("\n=== enabling "+kcvp+" ===")
        aml = setup_and_train(True)
        models = get_partitioned_model_names(aml.leaderboard)
        keys = list_keys_in_memory()
        preds = len(keys['cv_predictions'])
        expected = len(models) * (nfolds + 1)  # +1 for holdout prediction
        assert preds == expected, "missing CV predictions in memory, got {actual}, expected {expected}".format(actual=preds, expected=expected)
        for m in models.base:
            assert_cv_predictions_on_model(m)
        for m in models.se:
            assert_cv_predictions_on_model(h2o.get_model(m).metalearner()['name'])

    def test_param_disabled():
        print("\n=== disabling "+kcvp+" ===")
        aml = setup_and_train(False)
        models = get_partitioned_model_names(aml.leaderboard)
        keys = list_keys_in_memory()
        preds = len(keys['cv_predictions'])
        assert preds == 0, "{preds} CV predictions were not cleaned from memory".format(preds=preds)
        for m in models.base:
            assert_cv_predictions_on_model(m, False)
        for m in models.se:
            assert not h2o.get_model(h2o.get_model(m).metalearner()['name']).cross_validation_predictions()

    def test_SE_retraining_fails_when_param_disabled():
        print("\n=== disabling "+kcvp+" and retraining ===")
        total_runs = 4
        aml = setup_and_train(False)  # first run
        first_models = get_partitioned_model_names(aml.leaderboard)
        first_bof = next(m for m in first_models.se if re.search(r'_BestOfFamily_', m))
        ds = import_dataset()
        for i in range(total_runs - 1):
            aml.train(y=ds.target, training_frame=ds.train)
        models = get_partitioned_model_names(aml.leaderboard)
        first_se_all_models = [m for m in first_models.se if re.search(r'_AllModels_', m)]
        se_all_models = [m for m in models.se if re.search(r'_AllModels_', m)]
        se_best_of_family = [m for m in models.se if re.search(r'_BestOfFamily_', m)]
        lb = aml.leaderboard
        print(lb.head(lb.nrows))

        assert len(models.se) == len(se_all_models) + len(se_best_of_family)
        assert len(se_all_models) == len(first_se_all_models), \
            "expecting only the {} first StackedEnsemble_AllModels, but got {}".format(len(first_se_all_models), len(se_all_models))
        assert se_all_models[0] in first_models.se, "first StackedEnsemble_AllModels got replaced by new one"
        if len(se_best_of_family) > 1:
            assert first_bof in se_best_of_family, "first StackedEnsemble_BestOfFamily disappeared after multiple runs"
            row_of = lambda id: lb[lb['model_id'] == id]
            first_bof_row = row_of(first_bof)
            assert all(all(row[i] == first_bof_row[i] for i in range(1, lb.ncols)) for row in [row_of(se) for se in se_best_of_family]), \
                "expecting possibly 2+ similar StackedEnsemble_BestOfFamily (corner case), but managed to obtain 2 different ones!"
        else:
            assert len(se_best_of_family) == 1, "expecting only the first StackedEnsemble_BestOfFamily, but got {}".format(len(se_best_of_family))
            assert se_best_of_family[0] == first_bof, "first StackedEnsemble_Best_of_Family got replaced by new one"

    def test_SE_retraining_works_when_param_enabled():
        print("\n=== enabling "+kcvp+" and retraining ===")
        total_runs = 4
        aml = setup_and_train(True)  # first run
        ds = import_dataset()
        for i in range(total_runs - 1):
            aml.train(y=ds.target, training_frame=ds.train)
        models = get_partitioned_model_names(aml.leaderboard)
        se_all_models = [m for m in models.se if re.search(r'_AllModels_', m)]
        se_best_of_family = [m for m in models.se if re.search(r'_BestOfFamily_', m)]
        assert len(models.se) == len(se_all_models) + len(se_best_of_family)
        assert len(se_best_of_family) + len(se_all_models) >= total_runs, "some StackedEnsembles are missing"

    return [
        test_default_behaviour,
        test_param_enabled,
        test_param_disabled,
        test_SE_retraining_fails_when_param_disabled,
        test_SE_retraining_works_when_param_enabled
    ]


def test_suite_clean_cv_models():
    kcvm = 'keep_cross_validation_models'
    nfolds = 5

    def setup_and_train(param_enabled=None):
        h2o.remove_all()
        ds = import_dataset()
        state = 'enabled' if param_enabled is True else 'disabled' if param_enabled is False else 'default'
        if param_enabled is None:
            aml = H2OAutoML(project_name='keep_cross_validation_models'+state,
                            nfolds=nfolds, max_models=3, seed=1)
        else:
            aml = H2OAutoML(project_name='keep_cross_validation_models'+state,
                            nfolds=nfolds, max_models=8, seed=1,
                            keep_cross_validation_models=param_enabled)

        aml.train(y=ds.target, training_frame=ds.train)
        # print(aml.leaderboard)
        return aml

    def test_default_behaviour():
        print("\n=== "+kcvm+" default behaviour ===")
        aml = setup_and_train()
        models = get_partitioned_model_names(aml.leaderboard)
        check_model_property(models.se, kcvm, False)
        check_model_property(models.base, kcvm, True, False, True)
        keys = list_keys_in_memory()
        tot, cv = len(keys['models_all']), len(keys['cv_models'])
        print("total models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
        assert tot > 0, "no models left in memory"
        assert cv == 0, "{cv} CV models were not cleaned from memory".format(cv=cv)
        for m in models.base:
            assert not h2o.get_model(m).cross_validation_models(), "unexpected cv models for model "+m
        for m in models.se:
            metal = h2o.get_model(h2o.get_model(m).metalearner()['name'])
            assert not metal.cross_validation_models(), "unexpected cv models for metalearner of model "+m

    def test_param_enabled():
        print("\n=== enabling "+kcvm+" ===")
        aml = setup_and_train(True)
        models = get_partitioned_model_names(aml.leaderboard)
        check_model_property(models.se, kcvm, False)
        check_model_property(models.base, kcvm, True, True, True)
        keys = list_keys_in_memory()
        tot, cv = len(keys['models_all']), len(keys['cv_models'])
        print("total models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
        assert tot > 0, "no models left in memory"
        expected = len(models.all) * nfolds
        assert cv == expected, "missing CV models in memory, got {actual}, expected {expected}".format(actual=cv, expected=expected)
        for m in models.base:
            assert h2o.get_model(m).cross_validation_models(), "missing cv models for model "+m
        for m in models.se:
            metal = h2o.get_model(h2o.get_model(m).metalearner()['name'])
            assert metal.cross_validation_models(), "missing cv models for metalearner of model "+m

    def test_param_disabled():
        print("\n=== disabling "+kcvm+" ===")
        aml = setup_and_train(False)
        models = get_partitioned_model_names(aml.leaderboard)
        check_model_property(models.se, kcvm, False)
        check_model_property(models.base, kcvm, True, False, True)
        keys = list_keys_in_memory()
        tot, cv = len(keys['models_all']), len(keys['cv_models'])
        print("total models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
        assert tot > 0, "no models left in memory"
        assert cv == 0, "{cv} CV models were not cleaned from memory".format(cv=cv)
        for m in models.base:
            assert not h2o.get_model(m).cross_validation_models(), "unexpected cv models for model "+m
        for m in models.se:
            metal = h2o.get_model(h2o.get_model(m).metalearner()['name'])
            assert not metal.cross_validation_models(), "unexpected cv models for metalearner of model "+m

    return [
        test_default_behaviour,
        test_param_enabled,
        test_param_disabled,
    ]


def test_suite_remove_automl():

    def contains_leaderboard(project_name, keys):
        return "Leaderboard_{}".format(project_name) in keys['all'].values

    def contains_event_log(project_name, keys):
        return "Events_{}".format(project_name) in keys['all'].values

    def frame_in_cluster(frame):
        # reload the first row of the frame to verify that no vec has been removed
        return frame.key is not None and H2OFrame.get_frame(frame.key, rows=1) is not None

    def test_remove_automl_with_xval():
        ds = import_dataset()
        project_name = 'aml_with_xval_remove_test'
        max_models = 5
        nfolds = 5
        aml = H2OAutoML(project_name=project_name,
                        nfolds=nfolds,
                        max_models=max_models,
                        seed=1)
        aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)

        keys = list_keys_in_memory()
        assert aml.key.startswith(project_name)
        assert contains_leaderboard(aml.key, keys)
        assert contains_event_log(aml.key, keys)
        num_SEs = len(keys['metalearners'])
        print({k: len(v) for k, v in keys.items()})
        expectations = dict(
            models_base=max_models + num_SEs,
            cv_models=0,
            predictions=0,
            metrics=(max_models * 3  # for each non-SE model, 1 on training_frame, 1 on validation_frame, 1 on leaderboard_frame
                     + (num_SEs * 2)  # for each SE model, 1 on training frame, 1 on leaderboard frame
                     + (num_SEs * 2)  # for each SE metalearner, 1+1 on levelone training+validation
                     + (1 if any(("DeepLearning" in x for x in keys["metrics"])) else 0)  # DeepLearning has 2 training metrics (IDK why)
                     )
        )
        for k, v in expectations.items():
            assert len(keys[k]) == v, "expected {} {}, but got {}".format(v, k, len(keys[k]))

        h2o.remove(aml)
        clean = list_keys_in_memory()
        print(clean['all'].values)
        assert not contains_leaderboard(aml.key, clean)
        assert not contains_event_log(aml.key, clean)
        assert len(clean['models_base']) == 0
        assert len(clean['cv_models']) == 0
        assert len(clean['models_all']) == 0
        assert len(clean['predictions']) == 0
        assert len(clean['metrics']) == 0
        assert len(clean['automl']) == 0
        for frame in [ds.train, ds.valid, ds.test]:
            assert frame_in_cluster(frame), "frame {} has been removed from cluster".format(frame.frame_id)

    def test_remove_automl_with_xval_when_keeping_all_cv_details():
        ds = import_dataset()
        project_name = 'aml_with_xval_remove_test'
        max_models = 5
        nfolds = 5
        aml = H2OAutoML(project_name=project_name,
                        nfolds=nfolds,
                        max_models=max_models,
                        seed=1,
                        keep_cross_validation_predictions=True,
                        keep_cross_validation_fold_assignment=True,
                        keep_cross_validation_models=True)
        aml.train(y=ds.target, training_frame=ds.train)

        keys = list_keys_in_memory()
        # print(keys['all'].values)
        assert aml.key.startswith(project_name)
        assert contains_leaderboard(aml.key, keys)
        assert contains_event_log(aml.key, keys)
        num_SEs = len(keys['metalearners']) / (nfolds + 1)  # keeping cv models, so metalearners include cv models
        print({k: len(v) for k, v in keys.items()})
        expectations = dict(
            models_base=max_models + num_SEs,
            cv_models=(max_models+num_SEs) * nfolds,  # 1 cv model per fold for all models, incl. SEs
            predictions=(len(keys['cv_models'])  # cv predictions
                         + len(keys['models_base'])  # cv holdout predictions
                         ),
            metrics=(len(keys['cv_models']) * 3  # for each cv model, 1 on training frame, 1 on validation frame (=training for cv), one on adapted frame (to be removed with PUBDEV-6638)
                     + len(keys['models_base'])  # for each model, 1 on training_frame
                     + (num_SEs * 1)  # for each SE, 1 on levelone training
                     + (1 if any(("DeepLearning" in x for x in keys["metrics"])) else 0)  # DeepLearning has 2 training metrics (IDK why)
                     )
        )
        for k, v in expectations.items():
            assert len(keys[k]) == v, "expected {} {}, but got {}".format(v, k, len(keys[k]))

        h2o.remove(aml)
        clean = list_keys_in_memory()
        print(clean['all'].values)
        assert not contains_leaderboard(aml.key, clean)
        assert not contains_event_log(aml.key, clean)
        assert len(clean['models_base']) == 0
        assert len(clean['cv_models']) == 0
        assert len(clean['models_all']) == 0
        assert len(clean['predictions']) == 0
        assert len(clean['metrics']) == 0
        assert len(clean['automl']) == 0
        for frame in [ds.train, ds.valid, ds.test]:
            assert frame_in_cluster(frame), "frame {} has been removed from cluster".format(frame.frame_id)

    def test_remove_automl_no_xval():
        ds = import_dataset()
        project_name = 'aml_no_xval_remove_test'
        max_models = 5
        aml = H2OAutoML(project_name=project_name,
                        nfolds=0,
                        max_models=max_models,
                        seed=1)
        aml.train(y=ds.target, training_frame=ds.train, blending_frame=ds.valid)

        keys = list_keys_in_memory()
        # print(keys['all'].values)
        assert aml.key.startswith(project_name)
        assert contains_leaderboard(aml.key, keys)
        assert contains_event_log(aml.key, keys)
        num_SEs = len(keys['metalearners'])
        print({k: len(v) for k, v in keys.items()})
        expectations = dict(
            models_base=max_models + num_SEs,
            cv_models=0,
            predictions=0,
            metrics=(len(keys['models_base']) * 2  # for each model, 1 on training_frame, 1 on leaderboard frame (those are extracted from original training_frame)
                     + max_models * 1  # for each non-SE model, 1 on validation frame
                     + (num_SEs * 2)  # for each SE metalearner, 1 on levelone training, 1 on levelone validation
                     )
        )
        for k, v in expectations.items():
            assert len(keys[k]) == v, "expected {} {}, but got {}".format(v, k, len(keys[k]))

        h2o.remove(aml)
        clean = list_keys_in_memory()
        print(clean['all'].values)
        assert not contains_leaderboard(aml.key, clean)
        assert not contains_event_log(aml.key, clean)
        assert len(clean['models_base']) == 0
        assert len(clean['cv_models']) == 0
        assert len(clean['models_all']) == 0
        assert len(clean['metrics']) == 0
        assert len(clean['predictions']) == 0
        assert len(clean['automl']) == 0
        for frame in [ds.train, ds.valid, ds.test]:
            assert frame_in_cluster(frame), "frame {} has been removed from cluster".format(frame.frame_id)

    def test_remove_automl_after_individual_manual_deletions():
        ds = import_dataset()
        project_name='aml_no_xval_remove_test'
        max_models = 3
        aml = H2OAutoML(project_name=project_name,
                        nfolds=0,
                        max_models=max_models,
                        seed=1)
        aml.train(y=ds.target, training_frame=ds.train, blending_frame=ds.blend)

        keys = list_keys_in_memory()
        # manually remove the first item for each category to verify robustness of global automl deletion
        # for example, to verify that exceptions (if any) are handled correctly when automl is trying to remove a base model that was already removed
        for k, v in keys.items():
            if k == 'all': continue
            if len(v) > 0:
                h2o.remove(v[0])

        h2o.remove(aml)
        clean = list_keys_in_memory()
        print(clean['all'].values)
        assert aml.key.startswith(project_name)
        assert not contains_leaderboard(aml.key, clean)
        assert not contains_event_log(aml.key, clean)
        assert len(clean['models_base']) == 0
        assert len(clean['cv_models']) == 0
        assert len(clean['models_all']) == 0
        assert len(clean['metrics']) == 0
        assert len(clean['predictions']) == 0
        assert len(clean['automl']) == 0
        for frame in [ds.train, ds.valid, ds.test]:
            assert frame_in_cluster(frame), "frame {} has been removed from cluster".format(frame.frame_id)

    return [
        test_remove_automl_with_xval,
        test_remove_automl_with_xval_when_keeping_all_cv_details,
        test_remove_automl_no_xval,
        test_remove_automl_after_individual_manual_deletions
    ]


pu.run_tests([
    test_suite_clean_cv_predictions(),
    test_suite_clean_cv_models(),
    test_suite_remove_automl()
])
