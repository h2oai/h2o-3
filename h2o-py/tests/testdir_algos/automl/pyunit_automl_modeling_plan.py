from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import h2o.exceptions
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset, get_partitioned_model_names


max_models = 5


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
        print(e)
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
                    max_models=3,
                    modeling_plan=[
                        dict(name='GLM', steps=[dict(id='def_1')]),
                        dict(name='GBM', alias='grids'),
                        dict(name='DRF', steps=[dict(id='def_1', group=5, weight=333)]),  # just testing that it is parsed correctly on backend (no model will be built due to the priority group + max_models)
                        dict(name='GBM', steps=[dict(id="def_1")]),
                    ],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    models = get_partitioned_model_names(aml.leaderboard)
    assert len(models.base) == 3
    assert len(models.se) == 0
    assert any('GLM' in name for name in models.base)
    assert any('GBM' in name for name in models.base)
    assert any('GBM_grid' in name for name in models.base)


def test_modeling_plan_using_simplified_syntax():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_plan_simple_syntax",
                    max_models=3,
                    modeling_plan=[
                        ('DRF', ['XRT', 'def_1']),
                        ('GBM', 'grids'),
                        ('StackedEnsemble',)
                    ],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    models = get_partitioned_model_names(aml.leaderboard)
    assert len(models.base) == 3
    assert len(models.se) > 2
    assert any('DRF' in name for name in models.base)
    assert any('XRT' in name for name in models.base)
    assert any('GBM_grid' in name for name in models.base)
    assert len([name for name in models.se if 'BestOfFamily' in name]) > 2  # we should get a BoF for group1 + one after GBM grid group.


def test_modeling_plan_using_minimal_syntax():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_plan_minimal_syntax",
                    max_models=5,
                    modeling_plan=['DRF', 'GLM', ('GBM', 'grids'), 'StackedEnsemble'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    models = get_partitioned_model_names(aml.leaderboard)
    assert len(models.base) == 5
    assert len(models.se) > 2
    assert any('DRF' in name for name in models.base)
    assert any('XRT' in name for name in models.base)
    assert any('GLM' in name for name in models.base)
    assert any('GBM_grid' in name for name in models.base)
    assert any('BestOfFamily' in name for name in models.se)
    assert any('AllModels' in name for name in models.se)


def test_modeling_steps():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_steps",
                    max_models=5,
                    modeling_plan=['DRF',
                                   dict(name='GBM', steps=[
                                       dict(id='def_3', group=2),
                                       dict(id='grid_1', weight=77)
                                   ]),
                                   ('GLM', 'defaults'),
                                   ('StackedEnsemble', 'defaults')],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    # we should now see the detailed steps sorted in their execution order.
    print(aml.modeling_steps)
    assert aml.modeling_steps == [
        {'name': 'DRF', 'steps': [{'id': 'def_1', 'group': 1, 'weight': 10},
                                  {'id': 'XRT', 'group': 1, 'weight': 10}]},
        {'name': 'GLM', 'steps': [{'id': 'def_1', 'group': 1, 'weight': 10}]},
        {'name': 'StackedEnsemble', 'steps': [{'id': 'best_of_family_1', 'group': 1, 'weight': 10}]},  # no all_1 as XRT is interpreted as not being of the same family as DRF (legacy decision). 
        {'name': 'GBM', 'steps': [{'id': 'def_3', 'group': 2, 'weight': 10},
                                  {'id': 'grid_1', 'group': 2, 'weight': 77}]},  # grids are 2nd group by default
        {'name': 'StackedEnsemble', 'steps': [{'id': 'best_of_family_2', 'group': 2, 'weight': 10}, 
                                              {'id': 'all_2', 'group': 2, 'weight': 10}]}
    ]

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
    models = get_partitioned_model_names(aml.leaderboard)
    assert len(models.base) == 3
    assert len(models.se) == 0


pu.run_tests([
    test_bad_modeling_plan_using_full_syntax,
    test_bad_modeling_plan_using_simplified_syntax,
    test_modeling_plan_using_full_syntax,
    test_modeling_plan_using_simplified_syntax,
    test_modeling_plan_using_minimal_syntax,
    test_modeling_steps,
    test_exclude_algos_is_applied_on_top_of_modeling_plan,
])
