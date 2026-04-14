import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator, H2OXGBoostEstimator
from h2o.exceptions import H2OServerError

def test_h_input_validation():
    """Test that h() method properly validates input parameters"""
    
    # Load dataset
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
    prostate['GLEASON'] = prostate['GLEASON'].asfactor()  # Make GLEASON categorical
    
    # Train a GBM model
    gbm = H2OGradientBoostingEstimator(ntrees=5, max_depth=5, seed=1234)
    gbm.train(x=['AGE', 'PSA', 'DPROS', 'DCAPS', 'VOL', 'GLEASON'], y='CAPSULE', training_frame=prostate)
    
    # Test 1: null/None vars parameter
    try:
        gbm.h(prostate, None)
        assert False, "Should have raised an error for None vars parameter"
    except (H2OServerError, ValueError) as e:
        print("Test 1 passed: None vars parameter raises error")
        assert "vars" in str(e).lower() or "variable" in str(e).lower()
    
    # Test 2: empty vars parameter
    try:
        gbm.h(prostate, [])
        assert False, "Should have raised an error for empty vars parameter"
    except (H2OServerError, ValueError) as e:
        print("Test 2 passed: Empty vars parameter raises error")
        assert "empty" in str(e).lower() or "vars" in str(e).lower()
    
    # Test 3: non-existent column
    try:
        gbm.h(prostate, ['AGE', 'NONEXISTENT_COLUMN'])
        assert False, "Should have raised an error for non-existent column"
    except H2OServerError as e:
        print("Test 3 passed: Non-existent column raises error")
        assert "does not exist" in str(e).lower() or "nonexistent" in str(e).lower()
    
    # Test 4: non-numeric (categorical) column
    try:
        gbm.h(prostate, ['AGE', 'GLEASON'])
        assert False, "Should have raised an error for non-numeric column"
    except H2OServerError as e:
        print("Test 4 passed: Non-numeric column raises error")
        assert "not numeric" in str(e).lower() or "categorical" in str(e).lower()
    
    # Test 5: Valid case - should not raise error
    try:
        h_val = gbm.h(prostate, ['AGE', 'PSA'])
        print(f"Test 5 passed: Valid input works correctly, H = {h_val}")
        assert h_val >= 0.0, "H statistic should be non-negative"
    except Exception as e:
        assert False, f"Valid input should not raise error: {e}"
    
    # Test 6: XGBoost should also validate input correctly
    if H2OXGBoostEstimator.available():
        xgb = H2OXGBoostEstimator(ntrees=5, max_depth=5, seed=1234)
        xgb.train(x=['AGE', 'PSA', 'DPROS', 'DCAPS', 'VOL', 'GLEASON'], y='CAPSULE', training_frame=prostate)
        
        try:
            xgb.h(prostate, ['AGE', 'GLEASON'])
            assert False, "XGBoost should have raised an error for non-numeric column"
        except H2OServerError as e:
            print("Test 6 passed: XGBoost also validates non-numeric columns")
            assert "not numeric" in str(e).lower()
        
        # Valid XGBoost case
        h_val_xgb = xgb.h(prostate, ['AGE', 'PSA'])
        print(f"XGBoost valid input works correctly, H = {h_val_xgb}")
        assert h_val_xgb >= 0.0, "H statistic should be non-negative"
    
    print("\nAll input validation tests passed!")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_h_input_validation)
else:
    test_h_input_validation()
