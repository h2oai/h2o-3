import os
import sys

sys.path.insert(1, "../../")

import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils


def checkpointing_test():
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    gbm = H2OGradientBoostingEstimator(ntrees=1)
    gbm.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)
    
    checkpointed_gbm = H2OGradientBoostingEstimator(ntrees=2, checkpoint=gbm)
    checkpointed_gbm.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)
    assert checkpointed_gbm.checkpoint == gbm

    checkpointed_gbm = H2OGradientBoostingEstimator(ntrees=2, checkpoint=gbm.model_id)
    checkpointed_gbm.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)
    assert checkpointed_gbm.checkpoint == gbm.model_id


def checkpointing_with_delete_test():
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    gbm = H2OGradientBoostingEstimator(ntrees=1)
    gbm.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)

    path = pyunit_utils.locate("results")

    gbm_path = h2o.save_model(gbm, path=path, force=True)
    h2o.remove_all([airlines.frame_id])
    gbm = h2o.load_model(gbm_path)

    checkpointed_gbm = H2OGradientBoostingEstimator(ntrees=2, checkpoint=gbm)
    checkpointed_gbm.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)
    assert checkpointed_gbm.checkpoint == gbm

    checkpointed_gbm = H2OGradientBoostingEstimator(ntrees=2, checkpoint=gbm.model_id)
    checkpointed_gbm.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)
    assert checkpointed_gbm.checkpoint == gbm.model_id


if __name__ == "__main__":
    pyunit_utils.run_tests([checkpointing_test, checkpointing_with_delete_test])
else:
    checkpointing_test()
