"""
Test for PUBDEV-5884: DRF and XRT early stopping configuration in AutoML

This test verifies that early stopping is disabled (stopping_rounds=0) for DRF and XRT
models in AutoML, since they only have 50 trees by default. With score_tree_interval=5,
this would result in only 10 scoring checkpoints, which is insufficient for reliable
early stopping.

See: https://github.com/h2oai/h2o-3/issues/PUBDEV-5884
"""

import sys
import os

sys.path.insert(1, os.path.join("..", "..", ".."))

import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu
from _automl_utils import import_dataset, get_partitioned_model_names


def test_drf_early_stopping_disabled():
    """Verify that DRF models have early stopping disabled in AutoML"""
    print("\n" + "="*80)
    print("TEST: DRF early stopping should be disabled")
    print("="*80)
    
    ds = import_dataset()
    aml = H2OAutoML(
        project_name="test_drf_stopping",
        include_algos=["DRF"],
        max_models=1,
        seed=42,
        verbosity="info"
    )
    aml.train(y=ds.target, training_frame=ds.train)
    
    # Get the DRF model from leaderboard
    drf_model = aml.leader
    print(f"\nDRF Model ID: {drf_model.model_id}")
    print(f"Model Algorithm: {drf_model.algo}")
    
    # Extract stopping_rounds parameter
    model_params = drf_model.params
    stopping_rounds = model_params.get('stopping_rounds', None)
    
    print(f"Stopping Rounds: {stopping_rounds}")
    print(f"Stopping Metric: {model_params.get('stopping_metric', 'N/A')}")
    print(f"Score Tree Interval: {model_params.get('score_tree_interval', 'N/A')}")
    
    # Verify early stopping is disabled
    assert stopping_rounds == 0, (
        f"DRF model should have stopping_rounds=0, but got {stopping_rounds}. "
        "This is to accommodate the 50-tree default with limited scoring checkpoints."
    )
    
    print("\n✓ DRF early stopping is correctly disabled (stopping_rounds=0)")


def test_xrt_early_stopping_disabled():
    """Verify that XRT (eXtremely Randomized Trees) has early stopping disabled"""
    print("\n" + "="*80)
    print("TEST: XRT early stopping should be disabled")
    print("="*80)
    
    ds = import_dataset()
    aml = H2OAutoML(
        project_name="test_xrt_stopping",
        include_algos=["DRF"],  # XRT is trained as part of DRF
        max_models=2,
        seed=42,
        verbosity="info"
    )
    aml.train(y=ds.target, training_frame=ds.train)
    
    # Find XRT model in the leaderboard (XRT is one of the DRF variants)
    leaderboard = aml.leaderboard
    print("\nLeaderboard models:")
    for i, row in enumerate(leaderboard.as_data_frame().head(3).itertuples()):
        print(f"  {i+1}. {row[1]} (model_id: {row[0]})")
    
    # Get both models and check their configurations
    for model_id in leaderboard['model_id'].as_data_frame().values.flatten():
        model = h2o.get_model(model_id)
        if 'XRT' in model_id or model.params.get('histogram_type') == 'Random':
            print(f"\nXRT Model: {model.model_id}")
            stopping_rounds = model.params.get('stopping_rounds', None)
            histogram_type = model.params.get('histogram_type', 'N/A')
            
            print(f"  Histogram Type: {histogram_type}")
            print(f"  Stopping Rounds: {stopping_rounds}")
            print(f"  Score Tree Interval: {model.params.get('score_tree_interval', 'N/A')}")
            
            # Verify early stopping is disabled for XRT
            assert stopping_rounds == 0, (
                f"XRT model should have stopping_rounds=0, but got {stopping_rounds}. "
                "This is to accommodate the 50-tree default with limited scoring checkpoints."
            )
            print("\n✓ XRT early stopping is correctly disabled")


def test_score_tree_interval_unchanged():
    """Verify that score_tree_interval remains at 5 (optimization point)"""
    print("\n" + "="*80)
    print("TEST: score_tree_interval should remain at 5")
    print("="*80)
    
    ds = import_dataset()
    aml = H2OAutoML(
        project_name="test_score_interval",
        include_algos=["DRF"],
        max_models=1,
        seed=42
    )
    aml.train(y=ds.target, training_frame=ds.train)
    
    drf_model = aml.leader
    score_tree_interval = drf_model.params.get('score_tree_interval', None)
    
    print(f"Score Tree Interval: {score_tree_interval}")
    
    # Verify score_tree_interval is at the expected value
    assert score_tree_interval == 5, (
        f"DRF model should have score_tree_interval=5, but got {score_tree_interval}"
    )
    
    print("✓ Score tree interval is correctly set to 5")


def test_drf_xrt_performance_unimpacted():
    """
    Verify that disabling early stopping doesn't negatively impact model quality.
    This is a smoke test - with only 1 model, we just verify it completes without error.
    """
    print("\n" + "="*80)
    print("TEST: DRF/XRT models should train successfully without early stopping")
    print("="*80)
    
    ds = import_dataset()
    aml = H2OAutoML(
        project_name="test_drf_quality",
        include_algos=["DRF"],
        max_models=1,
        seed=42,
        verbosity="info"
    )
    aml.train(y=ds.target, training_frame=ds.train)
    
    # Verify a model was trained
    assert aml.leader is not None, "No model was trained"
    print(f"\nTrained model: {aml.leader.model_id}")
    print(f"Algorithm: {aml.leader.algo}")
    
    # Verify we can get metrics
    if ds.train[ds.target].isfactor():
        # Classification
        auc = aml.leader.auc()
        print(f"Model AUC: {auc}")
        assert auc is not None and auc > 0, "Model should have a valid AUC"
    else:
        # Regression
        r2 = aml.leader.r2()
        print(f"Model R²: {r2}")
        assert r2 is not None, "Model should have a valid R² value"
    
    print("✓ DRF/XRT models train successfully without early stopping")


def suite():
    """Run all tests"""
    pu.run_tests([
        test_drf_early_stopping_disabled,
        test_xrt_early_stopping_disabled,
        test_score_tree_interval_unchanged,
        test_drf_xrt_performance_unimpacted,
    ])


if __name__ == "__main__":
    suite()
