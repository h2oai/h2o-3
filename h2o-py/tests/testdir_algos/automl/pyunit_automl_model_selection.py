from __future__ import print_function
import sys, os
import re

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import h2o.exceptions
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML

"""This test suite checks the AutoML parameters influencing the model selection pipeline"""

max_models = 5


def import_dataset(seed=0, larger=False):
    df = h2o.import_file(path=pu.locate("smalldata/prostate/{}".format("prostate_complete.csv.zip" if larger else "prostate.csv")))
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    #Split frames
    fr = df.split_frame(ratios=[.8,.1], seed=seed)
    #Set up train, validation, and test sets
    return pu.ns(train=fr[0], valid=fr[1], test=fr[2], target=target, target_idx=1)


def get_partitioned_model_names(leaderboard):
    model_names = [leaderboard[i, 0] for i in range(0, (leaderboard.nrows))]
    se_model_names = [m for m in model_names if m.startswith('StackedEnsemble')]
    non_se_model_names = [m for m in model_names if m not in se_model_names]
    return model_names, non_se_model_names, se_model_names


def test_exclude_algos():
    print("AutoML doesn't train models for algos listed in exclude_algos")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_exclude_algos",
                    exclude_algos=['DRF', 'GLM'],
                    max_models=max_models,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    assert not any(['DRF' in name or 'GLM' in name for name in non_se])
    assert len(se) >= 1


def test_include_algos():
    print("AutoML trains only models for algos listed in include_algos")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_include_algos",
                    include_algos=['GBM'],
                    max_models=max_models,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    assert all(['GBM' in name for name in non_se])
    assert len(se) == 0, "No StackedEnsemble should have been trained if not explicitly included to the existing include_algos"


def test_include_exclude_algos():
    print("include_algos and exclude_algos parameters are mutually exclusive")
    try:
        H2OAutoML(project_name="py_include_exclude_algos",
                  exclude_algos=['DRF', 'XGBoost'],
                  include_algos=['GBM'],
                  max_models=max_models,
                  seed=1)
        assert False, "Should have thrown AssertionError"
    except AssertionError as e:
        assert "Use either `exclude_algos` or `include_algos`, not both" in str(e)


def test_bad_modeling_plan_using_full_syntax():
    try:
        H2OAutoML(modeling_plan=[
            dict(steps=['def_1'])
        ])
    except AssertionError as e:
        assert "each definition must have a 'name' key" in str(e)

    try:
        H2OAutoML(modeling_plan=[
            dict(name="GBM", steps=['def_1'], alias='defaults')
        ])
    except AssertionError as e:
        assert "each definition must have only 1 or 2 keys" in str(e)

    try:
        H2OAutoML(modeling_plan=[
            dict(name="GBM", alias='all_steps')
        ])
    except AssertionError as e:
        assert "alias must be one of ['all', 'defaults', 'grids']" in str(e)

    try:
        H2OAutoML(modeling_plan=[
            dict(name="GBM", dummy=['def_1'])
        ])
    except AssertionError as e:
        assert "steps definitions support only the following keys: name, alias, steps" in str(e)

    try:
        H2OAutoML(modeling_plan=[
            dict(name="GBM", steps=['def_1'])
        ])
    except AssertionError as e:
        assert "each step must be a dict" in str(e)

    try:
        H2OAutoML(modeling_plan=[
            dict(name="GBM", steps=[dict(foo='def_1')])
        ])
    except AssertionError as e:
        assert "each step must have an 'id' key" in str(e)

    try:
        H2OAutoML(modeling_plan=[
            dict(name="GBM", steps=[dict(id='def_1', weight=3/4)])
        ])
    except AssertionError as e:
        assert "weight must be an integer" in str(e)


def test_bad_modeling_plan_using_simplified_syntax():
    try:
        H2OAutoML(modeling_plan=[
            ['GBM']
        ])
    except h2o.exceptions.H2OTypeError:
        pass

    try:
        H2OAutoML(modeling_plan=[
            ('GBM', 'defaults', ['def_1'])
        ])
    except AssertionError:
        pass

    try:
        H2OAutoML(modeling_plan=[
            ('GBM', 'dummy_alias')
        ])
    except h2o.exceptions.H2OTypeError:
        pass

    try:
        H2OAutoML(modeling_plan=[
            ('GBM', ('def_1', 'def_2'))
        ])
    except h2o.exceptions.H2OTypeError:
        pass


def test_modeling_plan_using_full_syntax():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_plan_full_syntax",
                    max_models=2,
                    modeling_plan=[
                        dict(name='GLM', steps=[dict(id='def_1')]),
                        dict(name='GBM', alias='grids'),
                        dict(name='DRF', steps=[dict(id='def_1', weight=333)]),  # just testing that it is parsed correctly on backend (no model won't be build due to max_models)
                    ],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    assert len(non_se) == 2
    assert len(se) == 0
    assert any('GLM' in name for name in non_se)
    assert any('GBM_grid' in name for name in non_se)


def test_modeling_plan_using_simplified_syntax():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_plan_simple_syntax",
                    max_models=3,
                    modeling_plan=[
                        ('DRF', ['XRT', 'def_1']),
                        ('GBM', 'grids'),
                        ('StackedEnsemble', ['best10'])
                    ],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    assert len(non_se) == 3
    assert len(se) == 1
    assert any('DRF' in name for name in non_se)
    assert any('XRT' in name for name in non_se)
    assert any('GBM_grid' in name for name in non_se)
    assert any('BestOfFamily' in name for name in se)


def test_modeling_plan_using_minimal_syntax():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_plan_minimal_syntax",
                    max_models=5,
                    modeling_plan=['DRF', 'GLM', ('GBM', 'grids'), 'StackedEnsemble'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    assert len(non_se) == 5
    assert len(se) == 2
    assert any('DRF' in name for name in non_se)
    assert any('XRT' in name for name in non_se)
    assert any('GLM' in name for name in non_se)
    assert any('GBM_grid' in name for name in non_se)
    assert any('BestOfFamily' in name for name in se)
    assert any('AllModels' in name for name in se)


def test_modeling_steps():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_steps",
                    max_models=5,
                    modeling_plan=['DRF',
                                   ('GLM', 'defaults'),
                                   dict(name='GBM', steps=[dict(id='grid_1', weight=77)]),
                                   'StackedEnsemble'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    print(aml.modeling_steps )
    assert aml.modeling_steps == [
        {'name': 'DRF', 'steps': [{'id': 'def_1', 'weight': 10}, {'id': 'XRT', 'weight': 10}]},
        {'name': 'GLM', 'steps': [{'id': 'def_1', 'weight': 10}]},
        {'name': 'GBM', 'steps': [{'id': 'grid_1', 'weight': 77}]},
        {'name': 'StackedEnsemble', 'steps': [{'id': 'best10', 'weight': 10}, {'id': 'all10', 'weight': 10}]}
    ]
    # assert aml.modeling_steps == [
    #     dict(name='DRF', steps=[dict(id='def_1', weight=10), dict(id='XRT', weight=10)]),
    #     dict(name='GLM', steps=[dict(id='def_1', weight=10)]),
    #     dict(name='GBM', steps=[dict(id='grid_1', weight=77)]),
    #     dict(name='StackedEnsemble', steps=[dict(id='best', weight=10), dict(id='all', weight=10)]),
    # ]

    new_aml = H2OAutoML(project_name="py_reinject_modeling_steps",
                        max_models=5,
                        modeling_plan=aml.modeling_steps,
                        seed=1)
    new_aml.train(y=ds.target, training_frame=ds.train)
    print(new_aml.leaderboard)
    assert aml.modeling_steps == new_aml.modeling_steps


def test_exclude_algos_is_applied_on_top_of_modeling_plan():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_plan_minimal_syntax",
                    max_models=5,
                    modeling_plan=['DRF', 'GLM', ('GBM', 'grids'), 'StackedEnsemble'],
                    exclude_algos=['GBM', 'StackedEnsemble'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    assert len(non_se) == 3
    assert len(se) == 0


def test_monotone_constraints():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_monotone_constraints",
                    monotone_constraints=dict(AGE=1, VOL=-1),  # constraints just for the sake of testing
                    max_models=6,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    model_names, _, _ = get_partitioned_model_names(aml.leaderboard)
    models_supporting_monotone_constraints = [n for n in model_names if re.match(r"GBM|XGBoost", n)]
    assert len(models_supporting_monotone_constraints) < len(model_names), \
        "models not supporting the constraint should not have been skipped"
    for m in models_supporting_monotone_constraints:
        model = h2o.get_model(m)
        value = next(v['actual'] for n, v in model.params.items() if n == 'monotone_constraints')
        assert isinstance(value, list)
        assert len(value) == 2
        age = next((v for v in value if v['key'] == 'AGE'), None)
        assert age is not None
        assert age['value'] == 1.0
        vol = next((v for v in value if v['key'] == 'VOL'), None)
        assert vol is not None
        assert vol['value'] == -1.0


def test_monotone_constraints_can_be_passed_as_algo_parameter():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_monotone_constraints",
                    algo_parameters=dict(
                        monotone_constraints=dict(AGE=1, VOL=-1),  # constraints just for the sake of testing
                        # ntrees=10,
                    ),
                    max_models=6,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    model_names, _, _ = get_partitioned_model_names(aml.leaderboard)
    models_supporting_monotone_constraints = [n for n in model_names if re.match(r"GBM|XGBoost", n)]
    assert len(models_supporting_monotone_constraints) < len(model_names), \
        "models not supporting the constraint should not have been skipped"
    for m in models_supporting_monotone_constraints:
        model = h2o.get_model(m)
        value = next(v['actual'] for n, v in model.params.items() if n == 'monotone_constraints')
        # print(param)
        assert isinstance(value, list)
        assert len(value) == 2
        age = next((v for v in value if v['key'] == 'AGE'), None)
        assert age is not None
        assert age['value'] == 1.0
        vol = next((v for v in value if v['key'] == 'VOL'), None)
        assert vol is not None
        assert vol['value'] == -1.0

    # models_supporting_ntrees = [n for n in model_names if re.match(r"DRF|GBM|XGBoost|XRT", n)]
    # assert len(models_supporting_ntrees) > 0
    # for m in models_supporting_ntrees:
    #     model = h2o.get_model(m)
    #     value = next(v['actual'] for n, v in model.params.items() if n == 'ntrees')
    #     assert value == 10


def test_algo_parameter_can_be_applied_only_to_a_specific_algo():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_specific_algo_param",
                    algo_parameters=dict(
                        GBM__monotone_constraints=dict(AGE=1)
                    ),
                    max_models=6,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    model_names, _, _ = get_partitioned_model_names(aml.leaderboard)
    models_supporting_monotone_constraints = [n for n in model_names if re.match(r"GBM|XGBoost", n)]
    assert next((m for m in models_supporting_monotone_constraints if m.startswith('GBM')), None), "There should be at least one GBM model"
    for m in models_supporting_monotone_constraints:
        model = h2o.get_model(m)
        mc_value = next(v['actual'] for n, v in model.params.items() if n == 'monotone_constraints')
        if m.startswith('GBM'):
            assert isinstance(mc_value, list)
            age = next((v for v in mc_value if v['key'] == 'AGE'), None)
            assert age is not None
            assert age['value'] == 1.0
        else:
            assert mc_value is None


def test_cannot_set_unauthorized_algo_parameter():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_unauthorized_algo_param",
                    algo_parameters=dict(
                        score_tree_interval=7
                    ),
                    max_models=6,
                    seed=1)
    try:
        aml.train(y=ds.target, training_frame=ds.train)
    except h2o.exceptions.H2OResponseError as e:
        assert "algo_parameters: score_tree_interval" in str(e)


def test_exploitation_disabled():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_exploitation_ratio_disabled",
                    exploitation_ratio=.0,
                    max_models=6,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    assert 'start_GBM_lr_annealing' not in aml.training_info
    assert 'start_XGBoost_lr_search' not in aml.training_info


def test_exploitation_doesnt_impact_max_models():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_exploitation_ratio_max_models",
                    exploitation_ratio=.1,
                    max_models=6,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    assert 'start_GBM_lr_annealing' in aml.training_info
    assert 'start_XGBoost_lr_search' in aml.training_info
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    assert len(non_se) == 6
    assert len(se) == 5

# FIXME: THIS DOESN'T WORK WITH MULTIPLE SEs
# def test_exploitation_impacts_exploration_duration():
#     ds = import_dataset()
#     planned_duration = 30
#     aml = H2OAutoML(project_name="py_exploitation_ratio_max_runtime",
#                     exploitation_ratio=.5,  # excessive ratio on purpose, due to training overheads in multinode
#                     exclude_algos=['DeepLearning', 'XGBoost'],  # removing some algos for the same reason as above
#                     max_runtime_secs=planned_duration,
#                     seed=1,
#                     # verbosity='debug'
#                     )
#     aml.train(y=ds.target, training_frame=ds.train)
#     automl_start = int(aml.training_info['start_epoch'])
#     assert 'start_GBM_lr_annealing' in aml.training_info
#     # assert 'start_XGBoost_lr_search' in aml.training_info
#     exploitation_start = int(aml.training_info['start_GBM_lr_annealing'])
#     exploration_duration = exploitation_start - automl_start
#     se_start = int(aml.training_info['start_StackedEnsemble_best90'])
#     exploitation_duration = se_start - exploitation_start
#     # can't reliably check duration ratio
#     assert 0 < exploration_duration < planned_duration
#     print(aml.leaderboard)
#     print(exploitation_duration)
#     print(exploration_duration)
#     assert 0 < exploitation_duration < exploration_duration


pu.run_tests([
    test_exclude_algos,
    test_include_algos,
    test_include_exclude_algos,
    test_bad_modeling_plan_using_full_syntax,
    test_bad_modeling_plan_using_simplified_syntax,
    test_modeling_plan_using_full_syntax,
    test_modeling_plan_using_simplified_syntax,
    test_modeling_plan_using_minimal_syntax,
    test_modeling_steps,
    test_exclude_algos_is_applied_on_top_of_modeling_plan,
    test_monotone_constraints,
    test_monotone_constraints_can_be_passed_as_algo_parameter,
    test_algo_parameter_can_be_applied_only_to_a_specific_algo,
    test_cannot_set_unauthorized_algo_parameter,
    test_exploitation_disabled,
    test_exploitation_doesnt_impact_max_models,
    #test_exploitation_impacts_exploration_duration,
])
