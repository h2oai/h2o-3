from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.automl import H2OAutoML, get_leaderboard
from tests import pyunit_utils as pu

from _automl_utils import import_dataset, get_partitioned_model_names


def test_stacked_ensembles_are_trained_after_max_models():
    print("Check that Stacked Ensembles are still trained after max models have been trained")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_SE_after_max_models", seed=1, max_models=5)
    aml.train(y=ds.target, training_frame=ds.train)

    se = get_partitioned_model_names(aml.leaderboard).se
    assert len(se) == 2, "StackedEnsemble should still be trained after max models have been reached"


def test_stacked_ensembles_are_trained_with_blending_frame_even_if_nfolds_eq_0():
    print("Check that we can disable cross-validation when passing a blending frame and that Stacked Ensembles are trained using this frame.")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_blending_frame", seed=1, max_models=5, nfolds=0)
    aml.train(y=ds.target, training_frame=ds.train, blending_frame=ds.valid, leaderboard_frame=ds.test)

    se = get_partitioned_model_names(aml.leaderboard).se
    assert len(se) == 2, "In blending mode, StackedEnsemble should still be trained in spite of nfolds=0."
    for m in se:
        model = h2o.get_model(m)
        assert model.params['blending_frame']['actual']['name'] == ds.valid.frame_id
        assert model._model_json['output']['stacking_strategy'] == 'blending'


def test_optional_SEs_trained_in_non_reproducible_mode():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_SEs_non_reproducible_mode", 
                    seed=1, 
                    max_runtime_secs=30,
                    include_algos=['StackedEnsemble', 'GLM', 'DRF'])  # 1 base model in each group: 1, 2, 3
    aml.train(y=ds.target, training_frame=ds.train)
    lb = get_leaderboard(aml, ['provider', 'step']).as_data_frame()
    print(lb)
    steps_SE = lb.query("provider == 'StackedEnsemble'").step.to_list()
    assert len(steps_SE) > 2
    assert 'best_of_family_1' not in steps_SE, "no SE should be built for first group (1 base nodel only)"
    assert 'best_of_family_2' in steps_SE, 'SE best_of_family from group 2 is missing'
    assert 'best_of_family_3' in steps_SE, 'SE best_of_family from group 3 is missing'
    assert 'best_of_family_4' not in steps_SE, 'all other SEs should be optional ones'
    assert 'all_1' not in steps_SE, 'all other SEs should be optional ones'
    assert 'all_2' not in steps_SE, 'all other SEs should be optional ones'
    assert 'all_3' not in steps_SE, 'all other SEs should be optional ones'
    assert 'best_of_family_gbm' in steps_SE, 'optional SE best_of_family should have been trained'


def test_optional_SEs_not_trained_in_reproducible_mode():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_SEs_reproducible_mode", 
                    seed=1, 
                    max_runtime_secs=30,
                    max_models=3,
                    include_algos=['StackedEnsemble', 'GLM', 'GBM'])  # 2 base model in group 1, 1 in group 2
    aml.train(y=ds.target, training_frame=ds.train)
    lb = get_leaderboard(aml, ['provider', 'step']).as_data_frame()
    print(lb)
    steps_SE = lb.query("provider == 'StackedEnsemble'").step.to_list()
    assert len(steps_SE) == 2
    assert 'best_of_family_1' not in steps_SE, "no SE should be built for first group (sequential reproducible mode)"
    assert 'best_of_family_2' not in steps_SE, "no SE should be built for second group (sequential reproducible mode)"
    assert 'best_of_family_3' not in steps_SE, "no SE should be built for third group (sequential reproducible mode)"
    assert 'best_of_family_xglm' in steps_SE, "final SE is missing"
    assert 'all_xglm' in steps_SE, "final SE is missing"
    assert 'best_of_family_gbm' not in steps_SE, 'no optional SE should be trained (sequential reproducible mode)'
    
     
pu.run_tests([
    test_stacked_ensembles_are_trained_after_max_models,
    test_stacked_ensembles_are_trained_with_blending_frame_even_if_nfolds_eq_0,
    test_optional_SEs_trained_in_non_reproducible_mode,
    test_optional_SEs_not_trained_in_reproducible_mode
])
