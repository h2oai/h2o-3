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
    print(checkpointed_gbm.checkpoint)
    assert checkpointed_gbm.checkpoint == gbm.model_id

    checkpointed_gbm = H2OGradientBoostingEstimator(ntrees=2, checkpoint=gbm.model_id)
    checkpointed_gbm.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)
    assert checkpointed_gbm.checkpoint == gbm.model_id
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(checkpointing_test)
else:
    checkpointing_test()
