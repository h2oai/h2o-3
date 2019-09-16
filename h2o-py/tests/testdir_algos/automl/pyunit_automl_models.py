from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import h2o.exceptions
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML

"""This test suite checks the """

max_models = 2


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
    assert len(se) == 2


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
        assert "Use either include_algos or exclude_algos" in str(e)


def test_bad_training_plan_using_full_syntax():
    try:
        H2OAutoML(training_plan=[
            dict(steps=['def_1'])
        ])
    except AssertionError as e:
        assert "each definition must have a 'name' key" in str(e)

    try:
        H2OAutoML(training_plan=[
            dict(name="GBM", steps=['def_1'], alias='defaults')
        ])
    except AssertionError as e:
        assert "each definition must have only 1 or 2 keys" in str(e)

    try:
        H2OAutoML(training_plan=[
            dict(name="GBM", alias='all_steps')
        ])
    except AssertionError as e:
        assert "alias must be one of ['all', 'defaults', 'grids']" in str(e)

    try:
        H2OAutoML(training_plan=[
            dict(name="GBM", dummy=['def_1'])
        ])
    except AssertionError as e:
        assert "steps definitions support only the following keys: name, alias, steps" in str(e)

    try:
        H2OAutoML(training_plan=[
            dict(name="GBM", steps=['def_1'])
        ])
    except AssertionError as e:
        assert "each step must be a dict" in str(e)

    try:
        H2OAutoML(training_plan=[
            dict(name="GBM", steps=[dict(foo='def_1')])
        ])
    except AssertionError as e:
        assert "each step must have an 'id' key" in str(e)

    try:
        H2OAutoML(training_plan=[
            dict(name="GBM", steps=[dict(id='def_1', weight=3/4)])
        ])
    except AssertionError as e:
        assert "weight must be an integer" in str(e)


def test_bad_training_plan_using_simplified_syntax():
    try:
        H2OAutoML(training_plan=[
            ['GBM']
        ])
    except h2o.exceptions.H2OTypeError:
        pass

    try:
        H2OAutoML(training_plan=[
            ('GBM', 'defaults', ['def_1'])
        ])
    except AssertionError:
        pass

    try:
        H2OAutoML(training_plan=[
            ('GBM', 'dummy_alias')
        ])
    except h2o.exceptions.H2OTypeError:
        pass

    try:
        H2OAutoML(training_plan=[
            ('GBM', ('def_1', 'def_2'))
        ])
    except h2o.exceptions.H2OTypeError:
        pass


def test_training_plan_using_full_syntax():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_training_plan_full_syntax",
                    max_models=2,
                    training_plan=[
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


def test_training_plan_using_simplified_syntax():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_training_plan_simple_syntax",
                    max_models=3,
                    training_plan=[
                        ('DRF', ['XRT', 'def_1']),
                        ('GBM', 'grids'),
                        ('StackedEnsemble', ['best'])
                    ],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    assert len(non_se) == 3
    assert len(se) == 1
    assert any('DRF' in name for name in non_se)
    assert any('XRT' in name for name in non_se)
    assert any('GBM_grid' in name for name in non_se)
    assert any('BestOfFamily' in name for name in se)


def test_training_plan_using_minimal_syntax():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_training_plan_minimal_syntax",
                    max_models=5,
                    training_plan=['DRF', 'GLM', ('GBM', 'grids'), 'StackedEnsemble'],
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


def test_executed_plan():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_executed_plan",
                    max_models=5,
                    training_plan=['DRF', 'GLM', ('GBM', 'grids'), 'StackedEnsemble'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    assert aml.executed_plan == [
        dict(name='DRF', steps=[dict(id='def_1', weight=10), dict(id='XRT', weight=10)]),
        dict(name='GLM', steps=[dict(id='def_1', weight=10)]),
        dict(name='GBM', steps=[dict(id='grid_1', weight=60)]),
        dict(name='StackedEnsemble', steps=[dict(id='best', weight=10), dict(id='all', weight=10)]),
    ]

    new_aml = H2OAutoML(project_name="py_reinject_executed_plan",
                        max_models=5,
                        training_plan=aml.executed_plan,
                        seed=1)
    new_aml.train(y=ds.target, training_frame=ds.train)
    print(new_aml.leaderboard)
    assert aml.executed_plan == new_aml.executed_plan


def test_exclude_algos_is_applied_on_top_of_training_plan():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_training_plan_minimal_syntax",
                    max_models=5,
                    training_plan=['DRF', 'GLM', ('GBM', 'grids'), 'StackedEnsemble'],
                    exclude_algos=['GBM', 'StackedEnsemble'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    _, non_se, se = get_partitioned_model_names(aml.leaderboard)
    assert len(non_se) == 3
    assert len(se) == 0


pu.run_tests([
    test_exclude_algos,
    test_include_algos,
    test_include_exclude_algos,
    test_bad_training_plan_using_full_syntax,
    test_bad_training_plan_using_simplified_syntax,
    test_training_plan_using_full_syntax,
    test_training_plan_using_simplified_syntax,
    test_training_plan_using_minimal_syntax,
    test_executed_plan,
    test_exclude_algos_is_applied_on_top_of_training_plan
])
