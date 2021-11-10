from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.automl import H2OAutoML, get_leaderboard
from tests import pyunit_utils as pu

from _automl_utils import import_dataset, get_partitioned_model_names

max_models = 2


def test_stacked_ensembles_are_trained_after_max_models():
    print("Check that Stacked Ensembles are still trained after max models have been trained")
    max_models = 5
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_SE_after_max_models", seed=1, max_models=max_models)
    aml.train(y=ds.target, training_frame=ds.train)

    se = get_partitioned_model_names(aml.leaderboard).se
    assert len(se) > 3, "StackedEnsemble should still be trained after max models have been reached"


def test_stacked_ensembles_are_trained_with_blending_frame_even_if_nfolds_eq_0():
    print("Check that we can disable cross-validation when passing a blending frame and that Stacked Ensembles are trained using this frame.")
    max_models = 5
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_blending_frame", seed=1, max_models=max_models, nfolds=0)
    aml.train(y=ds.target, training_frame=ds.train, blending_frame=ds.valid, leaderboard_frame=ds.test)

    se = get_partitioned_model_names(aml.leaderboard).se
    assert len(se) > 3, "In blending mode, StackedEnsemble should still be trained in spite of nfolds=0."
    for m in se:
        model = h2o.get_model(m)
        assert model.params['blending_frame']['actual']['name'] == ds.valid.frame_id
        assert model._model_json['output']['stacking_strategy'] == 'blending'


def test_optional_SEs_trained_by_default_when_no_time_limit():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_SEs_with_no_time_limit", seed=1, max_models=3)
    aml.train(y=ds.target, training_frame=ds.train)
    lb = get_leaderboard(aml, ['provider', 'step']).as_data_frame()
    steps_SE = lb.query("provider == 'StackedEnsemble'").step.to_list()
    assert len(steps_SE) > 1
    assert 'best_of_family_1' in steps_SE, "default SE for first group is missing"
    assert 'best_of_family_2' not in steps_SE, 'all other SEs should be optional ones'
    assert 'all_1' not in steps_SE, 'all other SEs should be optional ones'
    assert 'all_2' not in steps_SE, 'all other SEs should be optional ones'
    
    
pu.run_tests([
    test_stacked_ensembles_are_trained_after_max_models,
    test_stacked_ensembles_are_trained_with_blending_frame_even_if_nfolds_eq_0,
    test_optional_SEs_trained_by_default_when_no_time_limit,
])
