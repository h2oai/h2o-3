import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator, H2ORandomForestEstimator


def test_auto_rebalance_parameter_is_set():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    fr[target] = fr[target].asfactor()

    gbm = H2OGradientBoostingEstimator(seed=42, auto_rebalance=False)
    gbm.train(y=target, training_frame=fr)
    assert not gbm.actual_params["auto_rebalance"], "Parameter is not set"
    assert not gbm.params["auto_rebalance"]["input"], "Parameter is not set"

    gbm = H2OGradientBoostingEstimator(seed=42)
    gbm.train(y=target, training_frame=fr)
    assert gbm.actual_params["auto_rebalance"], "Parameter is not set - default is True"
    assert gbm.params["auto_rebalance"]["input"], "Parameter is not set - default is True"

    try:
        H2ORandomForestEstimator(seed=42, auto_rebalance=False)
        assert False, "Should fail"
    except TypeError as e:
        assert "auto_rebalance" in str(e), "Test should fail and complain about auto_rebalance parameter"


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_auto_rebalance_parameter_is_set)
else:
    test_auto_rebalance_parameter_is_set()
