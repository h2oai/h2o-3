import os
import sys

sys.path.insert(1, "../../")

import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils, assert_equals


def test_checkpointing_gives_equal_model_summary():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    predictors = ["ID", "AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]
    response = "CAPSULE"    
    gbm = H2OGradientBoostingEstimator(ntrees=50, seed=1111)
    gbm.train(x=predictors, y=response, training_frame=prostate)
    
    checkpointed_gbm = H2OGradientBoostingEstimator(ntrees=100, seed=1111, checkpoint=gbm.model_id)
    checkpointed_gbm.train(x=predictors, y=response, training_frame=prostate)

    gbm_ref = H2OGradientBoostingEstimator(ntrees=100, seed=1111)
    gbm_ref.train(x=predictors, y=response, training_frame=prostate)
    assert checkpointed_gbm.checkpoint == gbm.model_id

    checkpoint_summary = checkpointed_gbm._model_json["output"]["model_summary"]
    expected_summary = gbm_ref._model_json["output"]["model_summary"]
    print(expected_summary)
    print(checkpoint_summary)
    assert_equals(expected_summary["model_size_in_bytes"][0], checkpoint_summary["model_size_in_bytes"][0])
    assert_equals(expected_summary["mean_depth"][0], checkpoint_summary["mean_depth"][0])
    assert_equals(expected_summary["min_leaves"][0], checkpoint_summary["min_leaves"][0])
    assert_equals(expected_summary["max_leaves"][0], checkpoint_summary["max_leaves"][0])
    assert_equals(expected_summary["mean_leaves"][0], checkpoint_summary["mean_leaves"][0])
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_checkpointing_gives_equal_model_summary)
else:
    test_checkpointing_gives_equal_model_summary()
