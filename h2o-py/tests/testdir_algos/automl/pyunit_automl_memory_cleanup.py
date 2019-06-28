from __future__ import print_function
import itertools as iter
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import re
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML
from h2o.frame import H2OFrame


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
        cv_fold_assignment=cv_fold_assignment
    )


def prepare_data(seed=1):
    name = 'amldataset'
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"), destination_frame=name)
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    h2o.assign(df, name)
    fr = df.split_frame(ratios=[.8, .1],
                        destination_frames=[name+'_'+f for f in ['training', 'validation', 'leaderboard']],
                        seed=seed)
    train, valid, test = fr[0], fr[1], fr[2]
    return target, train, valid, test


def test_suite_clean_cv_predictions():
    kcvp = 'keep_cross_validation_predictions'
    nfolds = 5

    def setup_and_train(param_enabled=None):
        h2o.remove_all()
        target, train, _, _ = prepare_data()
        state = 'enabled' if param_enabled is True else 'disabled' if param_enabled is False else 'default'
        if param_enabled is None:
            aml = H2OAutoML(project_name='keep_cross_validation_predictions_'+state,
                            nfolds=nfolds, max_models=3, seed=1)
        else:
            aml = H2OAutoML(project_name='keep_cross_validation_predictions_'+state,
                            nfolds=nfolds, max_models=8, seed=1,
                            keep_cross_validation_predictions=param_enabled)

        aml.train(y=target, training_frame=train)
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
        _, non_se, se = get_partitioned_model_names(aml.leaderboard)
        keys = list_keys_in_memory()
        preds = len(keys['cv_predictions'])
        assert preds == 0, "{preds} CV predictions were not cleaned from memory".format(preds=preds)
        for m in non_se:
            assert_cv_predictions_on_model(m, False)
        for m in se:
            assert not h2o.get_model(h2o.get_model(m).metalearner()['name']).cross_validation_predictions()


    def test_param_enabled():
        print("\n=== enabling "+kcvp+" ===")
        aml = setup_and_train(True)
        models, non_se, se = get_partitioned_model_names(aml.leaderboard)
        keys = list_keys_in_memory()
        preds = len(keys['cv_predictions'])
        expected = len(models) * (nfolds + 1)  # +1 for holdout prediction
        assert preds == expected, "missing CV predictions in memory, got {actual}, expected {expected}".format(actual=preds, expected=expected)
        for m in non_se:
            assert_cv_predictions_on_model(m)
        for m in se:
            assert_cv_predictions_on_model(h2o.get_model(m).metalearner()['name'])

    def test_param_disabled():
        print("\n=== disabling "+kcvp+" ===")
        aml = setup_and_train(False)
        _, non_se, se = get_partitioned_model_names(aml.leaderboard)
        keys = list_keys_in_memory()
        preds = len(keys['cv_predictions'])
        assert preds == 0, "{preds} CV predictions were not cleaned from memory".format(preds=preds)
        for m in non_se:
            assert_cv_predictions_on_model(m, False)
        for m in se:
            assert not h2o.get_model(h2o.get_model(m).metalearner()['name']).cross_validation_predictions()

    def test_SE_retraining_fails_when_param_disabled():
        print("\n=== disabling "+kcvp+" and retraining ===")
        total_runs = 4
        aml = setup_and_train(False)  # first run
        _, _, first_se = get_partitioned_model_names(aml.leaderboard)
        first_bof = next(m for m in first_se if re.search(r'_BestOfFamily_', m))
        target, train, _, _ = prepare_data()
        for i in range(total_runs - 1):
            aml.train(y=target, training_frame=train)
        _, _, se = get_partitioned_model_names(aml.leaderboard)
        se_all_models = [m for m in se if re.search(r'_AllModels_', m)]
        se_best_of_family = [m for m in se if re.search(r'_BestOfFamily_', m)]
        lb = aml.leaderboard
        print(lb.head(lb.nrows))

        assert len(se) == len(se_all_models) + len(se_best_of_family)
        assert len(se_all_models) == 1, "expecting only the first StackedEnsemble_AllModels, but got {}".format(len(se_all_models))
        assert se_all_models[0] in first_se, "first StackedEnsemble_AllModels got replaced by new one"
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
        _, _, first_se = get_partitioned_model_names(aml.leaderboard)
        target, train, _, _ = prepare_data()
        for i in range(total_runs - 1):
            aml.train(y=target, training_frame=train)
        _, _, se = get_partitioned_model_names(aml.leaderboard)
        se_all_models = [m for m in se if re.search(r'_AllModels_', m)]
        se_best_of_family = [m for m in se if re.search(r'_BestOfFamily_', m)]
        assert len(se) == len(se_all_models) + len(se_best_of_family)
        assert len(se_all_models) == total_runs, "some StackedEnsemble_AllModels are missing"
        assert len(se_best_of_family) == total_runs, "some StackedEnsemble_BestOfFamily are missing"


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
        target, train, _, _ = prepare_data()
        state = 'enabled' if param_enabled is True else 'disabled' if param_enabled is False else 'default'
        if param_enabled is None:
            aml = H2OAutoML(project_name='keep_cross_validation_models'+state,
                            nfolds=nfolds, max_models=3, seed=1)
        else:
            aml = H2OAutoML(project_name='keep_cross_validation_models'+state,
                            nfolds=nfolds, max_models=8, seed=1,
                            keep_cross_validation_models=param_enabled)

        aml.train(y=target, training_frame=train)
        # print(aml.leaderboard)
        return aml


    def test_default_behaviour():
        print("\n=== "+kcvm+" default behaviour ===")
        aml = setup_and_train()
        models, non_se, se = get_partitioned_model_names(aml.leaderboard)
        check_model_property(se, kcvm, False)
        check_model_property(non_se, kcvm, True, False, True)
        keys = list_keys_in_memory()
        tot, cv = len(keys['models_all']), len(keys['cv_models'])
        print("total models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
        assert tot > 0, "no models left in memory"
        assert cv == 0, "{cv} CV models were not cleaned from memory".format(cv=cv)
        for m in non_se:
            assert not h2o.get_model(m).cross_validation_models(), "unexpected cv models for model "+m
        for m in se:
            metal = h2o.get_model(h2o.get_model(m).metalearner()['name'])
            assert not metal.cross_validation_models(), "unexpected cv models for metalearner of model "+m

    def test_param_enabled():
        print("\n=== enabling "+kcvm+" ===")
        aml = setup_and_train(True)
        models, non_se, se = get_partitioned_model_names(aml.leaderboard)
        check_model_property(se, kcvm, False)
        check_model_property(non_se, kcvm, True, True, True)
        keys = list_keys_in_memory()
        tot, cv = len(keys['models_all']), len(keys['cv_models'])
        print("total models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
        assert tot > 0, "no models left in memory"
        expected = len(models) * nfolds
        assert cv == expected, "missing CV models in memory, got {actual}, expected {expected}".format(actual=cv, expected=expected)
        for m in non_se:
            assert h2o.get_model(m).cross_validation_models(), "missing cv models for model "+m
        for m in se:
            metal = h2o.get_model(h2o.get_model(m).metalearner()['name'])
            assert metal.cross_validation_models(), "missing cv models for metalearner of model "+m

    def test_param_disabled():
        print("\n=== disabling "+kcvm+" ===")
        aml = setup_and_train(False)
        models, non_se, se = get_partitioned_model_names(aml.leaderboard)
        check_model_property(se, kcvm, False)
        check_model_property(non_se, kcvm, True, False, True)
        keys = list_keys_in_memory()
        tot, cv = len(keys['models_all']), len(keys['cv_models'])
        print("total models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
        assert tot > 0, "no models left in memory"
        assert cv == 0, "{cv} CV models were not cleaned from memory".format(cv=cv)
        for m in non_se:
            assert not h2o.get_model(m).cross_validation_models(), "unexpected cv models for model "+m
        for m in se:
            metal = h2o.get_model(h2o.get_model(m).metalearner()['name'])
            assert not metal.cross_validation_models(), "unexpected cv models for metalearner of model "+m

    return [
        test_default_behaviour,
        test_param_enabled,
        test_param_disabled,
    ]


def test_suite_remove_automl():

    def contains_leaderboard(project_name, keys):
        return "AutoML_Leaderboard_{}".format(project_name) in keys['all'].values

    def contains_event_log(project_name, keys):
        return "AutoML_Events_{}".format(project_name) in keys['all'].values

    def frame_in_cluster(frame):
        # reload the first row of the frame to verify that no vec has been removed
        return frame.key is not None and H2OFrame.get_frame(frame.key, rows=1) is not None


    def test_remove_automl_with_xval():
        target, train, valid, test = prepare_data()
        project_name = 'aml_with_xval_remove_test'
        max_models = 3
        nfolds = 5
        aml = H2OAutoML(project_name=project_name,
                        nfolds=nfolds,
                        max_models=max_models,
                        seed=1)
        aml.train(y=target, training_frame=train, validation_frame=valid, leaderboard_frame=test)

        keys = list_keys_in_memory()
        # print(keys['all'].values)
        assert contains_leaderboard(project_name, keys)
        assert contains_event_log(project_name, keys)
        expectations = dict(
            models_base=max_models + 2,  # 2 SEs
            cv_models=0,
            predictions=0,
            metrics=(max_models * 3  # for each non-SE model, 1 on training_frame, 1 on validation_frame, 1 on leaderboard_frame
                     + (2 * 2)  # for each SE model, 1 on training frame, 1 on leaderboard frame
                     + (2 * 4)  # for each SE metalearner, 1+1 on levelone training+validation, 1+1 for final scoring (preds_levelone training+leaderbird)
                     )
        )
        for k, v in expectations.items():
            assert len(keys[k]) == v, "expected {} {}, but got {}".format(v, k, len(keys[k]))

        h2o.remove(aml)
        clean = list_keys_in_memory()
        # print(clean['all'].values)
        assert not contains_leaderboard(project_name, clean)
        assert not contains_event_log(project_name, clean)
        assert len(clean['models_base']) == 0
        assert len(clean['cv_models']) == 0
        assert len(clean['models_all']) == 0
        assert len(clean['predictions']) == 0
        assert len(clean['metrics']) == 0
        assert len(clean['automl']) == 0
        for frame in [train, valid, test]:
            assert frame_in_cluster(frame), "frame {} has been removed from cluster".format(frame.frame_id)


    def test_remove_automl_with_xval_when_keeping_all_cv_details():
        target, train, valid, test = prepare_data()
        project_name = 'aml_with_xval_remove_test'
        max_models = 3
        nfolds = 5
        aml = H2OAutoML(project_name=project_name,
                        nfolds=nfolds,
                        max_models=max_models,
                        seed=1,
                        keep_cross_validation_predictions=True,
                        keep_cross_validation_fold_assignment=True,
                        keep_cross_validation_models=True)
        aml.train(y=target, training_frame=train)

        keys = list_keys_in_memory()
        # print(keys['all'].values)
        assert contains_leaderboard(project_name, keys)
        assert contains_event_log(project_name, keys)
        expectations = dict(
            models_base=max_models + 2,  # 2 SEs
            cv_models=(max_models+2) * nfolds,  # 1 cv model per fold for all models, incl. SEs
            predictions=(len(keys['cv_models'])  # cv predictions
                         + len(keys['models_base'])  # cv holdout predictions
                         ),
            metrics=(len(keys['cv_models']) * 3  # for each cv model, 1 on training frame, 1 on validation frame (=training for cv), 1 on frame with null key (what is this?)
                     + len(keys['models_base'])  # for each model, 1 on training_frame
                     + (2 * 2)  # for each SE, 1 on levelone training, 1 for final scoring (preds_levelone)
                     )
        )
        for k, v in expectations.items():
            assert len(keys[k]) == v, "expected {} {}, but got {}".format(v, k, len(keys[k]))

        h2o.remove(aml)
        clean = list_keys_in_memory()
        # print(clean['all'].values)
        assert not contains_leaderboard(project_name, clean)
        assert not contains_event_log(project_name, clean)
        assert len(clean['models_base']) == 0
        assert len(clean['cv_models']) == 0
        assert len(clean['models_all']) == 0
        assert len(clean['predictions']) == 0
        assert len(clean['metrics']) == 0
        assert len(clean['automl']) == 0
        for frame in [train, valid, test]:
            assert frame_in_cluster(frame), "frame {} has been removed from cluster".format(frame.frame_id)


    def test_remove_automl_no_xval():
        target, train, blend, test = prepare_data()
        project_name='aml_no_xval_remove_test'
        max_models = 3
        aml = H2OAutoML(project_name=project_name,
                        nfolds=0,
                        max_models=max_models,
                        seed=1)
        aml.train(y=target, training_frame=train, blending_frame=blend)

        keys = list_keys_in_memory()
        # print(keys['all'].values)
        assert contains_leaderboard(project_name, keys)
        assert contains_event_log(project_name, keys)
        expectations = dict(
            models_base=max_models + 2,  # 2 SEs
            cv_models=0,
            predictions=0,
            metrics=(len(keys['models_base']) * 2  # for each model, 1 on training_frame, 1 on leaderboard frame (those are extracted from original training_frame)
                     + max_models * 2  # for each non-SE model, 1 on blending frame, 1 on validation frame
                     + (2 * 4)  # for each SE metalearner, 1 on levelone training, 1 on levelone validation, 2 for final scoring (1 on training, 1 on leaderboard)
                     )
        )
        for k, v in expectations.items():
            assert len(keys[k]) == v, "expected {} {}, but got {}".format(v, k, len(keys[k]))

        h2o.remove(aml)
        clean = list_keys_in_memory()
        # print(clean['all'].values)
        assert not contains_leaderboard(project_name, clean)
        assert not contains_event_log(project_name, clean)
        assert len(clean['models_base']) == 0
        assert len(clean['cv_models']) == 0
        assert len(clean['models_all']) == 0
        assert len(clean['metrics']) == 0
        assert len(clean['predictions']) == 0
        assert len(clean['automl']) == 0
        for frame in [train, blend, test]:
            assert frame_in_cluster(frame), "frame {} has been removed from cluster".format(frame.frame_id)


    def test_remove_automl_after_individual_manual_deletions():
        target, train, blend, test = prepare_data()
        project_name='aml_no_xval_remove_test'
        max_models = 3
        aml = H2OAutoML(project_name=project_name,
                        nfolds=0,
                        max_models=max_models,
                        seed=1)
        aml.train(y=target, training_frame=train, blending_frame=blend)

        keys = list_keys_in_memory()
        # manually remove the first item for each category to verify robustness of global automl deletion
        for k, v in keys.items():
            if k == 'all': continue
            if len(v) > 0:
                h2o.remove(v[0])

        h2o.remove(aml)
        clean = list_keys_in_memory()
        # print(clean['all'].values)
        assert not contains_leaderboard(project_name, clean)
        assert not contains_event_log(project_name, clean)
        assert len(clean['models_base']) == 0
        assert len(clean['cv_models']) == 0
        assert len(clean['models_all']) == 0
        assert len(clean['metrics']) == 0
        assert len(clean['predictions']) == 0
        assert len(clean['automl']) == 0
        for frame in [train, blend, test]:
            assert frame_in_cluster(frame), "frame {} has been removed from cluster".format(frame.frame_id)


    return [
        test_remove_automl_with_xval,
        test_remove_automl_with_xval_when_keeping_all_cv_details,
        test_remove_automl_no_xval,
        test_remove_automl_after_individual_manual_deletions
    ]



pyunit_utils.run_tests(list(iter.chain.from_iterable([
    test_suite_clean_cv_predictions(),
    test_suite_clean_cv_models(),
    test_suite_remove_automl()
])))
