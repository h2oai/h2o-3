import os
import sys

sys.path.insert(1, "../../")

import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils, compare_frames_local


def test_gbm_in_training_checkpoint_gives_same_result():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    prostate["RACE"] = prostate["RACE"].asfactor()
    predictors = ["ID", "AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]
    response = "CAPSULE"

    path = pyunit_utils.locate("results")
    
    gbm = H2OGradientBoostingEstimator(model_id="gbm", ntrees=4, seed=1111, in_training_checkpoints_dir=path)
    gbm.train(x=predictors, y=response, training_frame=prostate)
     
    gbm_checkpoint = h2o.load_model(os.path.join(path, "%s.2" % gbm.model_id))

    checkpointed_gbm = H2OGradientBoostingEstimator(model_id="gbm_checkpointed", ntrees=10, seed=1111, checkpoint=gbm_checkpoint.model_id)
    checkpointed_gbm.train(x=predictors, y=response, training_frame=prostate)
    assert checkpointed_gbm.checkpoint == gbm_checkpoint.model_id

    gbm_reference = H2OGradientBoostingEstimator(ntrees=10, seed=1111)
    gbm_reference.train(x=predictors, y=response, training_frame=prostate)

    checkpoint_predict = checkpointed_gbm.predict(prostate)
    reference_predict = gbm_reference.predict(prostate)
    compare_frames_local(reference_predict, checkpoint_predict, 0)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gbm_in_training_checkpoint_gives_same_result)
else:
    test_gbm_in_training_checkpoint_gives_same_result()
