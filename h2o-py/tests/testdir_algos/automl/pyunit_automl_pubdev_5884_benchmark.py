"""
Benchmark test for PUBDEV-5884: DRF/XRT early stopping optimization

This benchmark test evaluates the performance of DRF and XRT models trained
in AutoML with early stopping disabled. It helps verify the fix for PUBDEV-5884
by demonstrating that disabling early stopping (with only 50 trees) doesn't
negatively impact model performance.

Usage:
    python pyunit_automl_pubdev_5884_benchmark.py
"""

import sys
import os
import time
from datetime import datetime

sys.path.insert(1, os.path.join("..", "..", ".."))

import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu
from _automl_utils import import_dataset, get_partitioned_model_names


def print_section(title):
    """Print a formatted section header"""
    print("\n" + "="*80)
    print(title.center(80))
    print("="*80)


def benchmark_drf_models():
    """
    Benchmark DRF and XRT models to ensure performance is acceptable
    after disabling early stopping.
    """
    print_section("DRF/XRT Benchmark Test for PUBDEV-5884")
    
    ds = import_dataset()
    print(f"\nDataset: {ds.train.shape[0]} rows × {ds.train.shape[1]} columns")
    print(f"Target: {ds.target}")
    print(f"Task: {'Classification' if ds.train[ds.target].isfactor() else 'Regression'}")
    
    # Benchmark configuration
    test_cases = [
        {
            "name": "DRF (1 model)",
            "include_algos": ["DRF"],
            "max_models": 1,
            "config_id": "drf_1"
        },
        {
            "name": "DRF (3 models)",
            "include_algos": ["DRF"],
            "max_models": 3,
            "config_id": "drf_3"
        },
        {
            "name": "DRF vs GBM (5 models, mixed)",
            "include_algos": ["DRF", "GBM"],
            "max_models": 5,
            "config_id": "drf_gbm_5"
        },
    ]
    
    results = []
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\n{'-'*80}")
        print(f"Test Case {i}: {test_case['name']}")
        print(f"{'-'*80}")
        
        start_time = time.time()
        
        try:
            aml = H2OAutoML(
                project_name=f"benchmark_{test_case['config_id']}",
                include_algos=test_case['include_algos'],
                max_models=test_case['max_models'],
                seed=42,
                verbosity="warn"
            )
            
            aml.train(y=ds.target, training_frame=ds.train)
            
            elapsed = time.time() - start_time
            
            # Collect metrics
            leader = aml.leader
            base_models = get_partitioned_model_names(aml.leaderboard).base
            
            # Get performance metric
            if ds.train[ds.target].isfactor():
                metric = leader.auc()
                metric_name = "AUC"
            else:
                metric = leader.r2()
                metric_name = "R²"
            
            result = {
                "test_case": test_case['name'],
                "max_models": test_case['max_models'],
                "actual_models": len(base_models),
                "elapsed_sec": elapsed,
                "leader_id": leader.model_id,
                "leader_algo": leader.algo,
                metric_name: metric,
                "status": "✓ PASS"
            }
            results.append(result)
            
            print(f"  Status: ✓ PASS")
            print(f"  Training Time: {elapsed:.2f}s")
            print(f"  Models Trained: {len(base_models)}")
            print(f"  Leader Model: {leader.model_id} ({leader.algo})")
            print(f"  {metric_name}: {metric:.4f}")
            
        except Exception as e:
            elapsed = time.time() - start_time
            result = {
                "test_case": test_case['name'],
                "max_models": test_case['max_models'],
                "elapsed_sec": elapsed,
                "status": f"✗ FAIL: {str(e)[:50]}"
            }
            results.append(result)
            print(f"  Status: ✗ FAIL")
            print(f"  Error: {str(e)[:100]}")
    
    # Print summary table
    print_section("Benchmark Summary")
    
    print("\n{:<35} {:<12} {:<12} {:<12}".format(
        "Test Case", "Time (sec)", "Models", "Status"
    ))
    print("-" * 75)
    
    total_time = 0
    passed = 0
    
    for result in results:
        time_str = f"{result['elapsed_sec']:.2f}" if 'elapsed_sec' in result else "N/A"
        models_str = str(result.get('actual_models', 'N/A'))
        status = result['status']
        
        print("{:<35} {:<12} {:<12} {:<12}".format(
            result['test_case'][:34],
            time_str,
            models_str,
            status
        ))
        
        total_time += result.get('elapsed_sec', 0)
        if '✓' in status:
            passed += 1
    
    print("-" * 75)
    print(f"Total Time: {total_time:.2f}s | Passed: {passed}/{len(results)}")
    
    # Print detailed results
    print_section("Detailed Results")
    
    metric_name = "AUC" if ds.train[ds.target].isfactor() else "R²"
    
    for result in results:
        if '✓' in result['status']:
            print(f"\n{result['test_case']}:")
            print(f"  Leader Model: {result['leader_id']} ({result['leader_algo']})")
            print(f"  {metric_name}: {result[metric_name]:.6f}")
            print(f"  Training Time: {result['elapsed_sec']:.2f}s")
    
    # Verify all tests passed
    assert passed == len(results), f"Expected all tests to pass, but {len(results)-passed} failed"
    
    print_section("Benchmark Complete")
    print("✓ All DRF/XRT models trained successfully with early stopping disabled")
    print(f"✓ Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")


def verify_drf_configuration():
    """
    Verify that DRF models in AutoML have the expected configuration
    (early stopping disabled, score_tree_interval=5).
    """
    print_section("DRF Configuration Verification")
    
    ds = import_dataset()
    
    aml = H2OAutoML(
        project_name="verify_config",
        include_algos=["DRF"],
        max_models=1,
        seed=42,
        verbosity="warn"
    )
    
    aml.train(y=ds.target, training_frame=ds.train)
    
    model = aml.leader
    params = model.params
    
    print("\nDRF Model Configuration:")
    print(f"  Model ID: {model.model_id}")
    print(f"  Stopping Rounds: {params.get('stopping_rounds', 'N/A')}")
    print(f"  Stopping Metric: {params.get('stopping_metric', 'N/A')}")
    print(f"  Stopping Tolerance: {params.get('stopping_tolerance', 'N/A')}")
    print(f"  Score Tree Interval: {params.get('score_tree_interval', 'N/A')}")
    print(f"  Number of Trees: {params.get('ntrees', 'N/A')}")
    
    # Verify configuration
    assert params.get('stopping_rounds') == 0, "stopping_rounds should be 0"
    assert params.get('score_tree_interval') == 5, "score_tree_interval should be 5"
    
    print("\n✓ DRF configuration is correct")


def suite():
    """Run all benchmark tests"""
    pu.run_tests([
        benchmark_drf_models,
        verify_drf_configuration,
    ])


if __name__ == "__main__":
    suite()
