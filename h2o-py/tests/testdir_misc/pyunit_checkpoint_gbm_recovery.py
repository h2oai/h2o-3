import os
import sys

sys.path.insert(1, "../../")

import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils
from h2o.grid import H2OGridSearch


def fail_tolerant_gbm(model, checkpoints, **training_params):
    if not hasattr(model, "checkpoint"):
        raise Exception(f"{model.__class__.__name__} is not support fail tolerance.")
    
    for key in checkpoints.keys():
        if not hasattr(model, key):
            raise Exception(f"{model.__class__.__name__} is not support attribute {key}.")
        keybackup = getattr(model, key)
        checkpoint = ""
        for value in checkpoints[key]:
            print(f"Training with {key}:{value}")
            setattr(model, key, value)
            setattr(model, "checkpoint", checkpoint)
            model.train(**training_params)
            checkpoint = model.model_id
        print(f"Training with {key}:{keybackup}")
        setattr(model, key, keybackup)
        setattr(model, "checkpoint", checkpoint)
        model.train(**training_params)
    

def checkpointing_test():
    # results = pyunit_utils.locate("results")
    # 
    # iris = h2o.import_file(
    #     "https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris.csv",
    #     destination_frame="iris"
    # )
    # hyper_parameters = {
    #     "learn_rate": [0.01, 0.02, 0.03, 0.04],
    #     "ntrees": [100, 120, 130, 140]
    # }
    # 
    # # train a cartesian grid of GBMs
    # gbm_grid = H2OGridSearch(
    #     model=H2OGradientBoostingEstimator,
    #     grid_id='gbm_grid',
    #     hyper_params=hyper_parameters,
    #     recovery_dir=results
    # )
    # gbm_grid.train(x=list(range(4)), y=4, training_frame=iris)
    # 
    # h2o.save_grid(results, gbm_grid.grid_id, save_params_references=True)
    # 
    # # on a new cluster recover grid
    # # this will load the training frame and any other objects required for training
    # grid = h2o.load_grid(
    #     f"{results}/gbm_grid",  # append grid ID to the recovery_dir
    #     load_params_references=True
    # )
    # train = h2o.get_frame("iris") # get reference to re-loaded training frame
    # grid.hyper_params = hyper_parameters # use original hyper-parameters
    # # continue grid training
    # grid.train(x=list(range(4)), y=4, training_frame=train)

    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    gbm = H2OGradientBoostingEstimator(ntrees=200, seed=123)
    
    # checkpointed_gbm = H2OGradientBoostingEstimator(ntrees=2, checkpoint=gbm)
    # checkpointed_gbm.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)
    # assert checkpointed_gbm.checkpoint == gbm
    # 
    # checkpointed_gbm = H2OGradientBoostingEstimator(ntrees=2, checkpoint=gbm.model_id)
    # checkpointed_gbm.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)
    # assert checkpointed_gbm.checkpoint == gbm.model_id

    fail_tolerant_gbm(gbm, {"ntrees": [100, 120, 130, 140]},
                      x=["Origin", "Dest"],
                      y="Distance",
                      training_frame=airlines,
                      validation_frame=airlines)
    print(gbm)

    gbm200 = H2OGradientBoostingEstimator(ntrees=200, seed=123)
    gbm200.train(x=["Origin", "Dest"], y="Distance", training_frame=airlines, validation_frame=airlines)
    
    print(gbm200)
    
    pred = gbm.predict(airlines)
    pred200 = gbm200.predict(airlines)
    
    print(pred)
    print(pred200)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(checkpointing_test)
else:
    checkpointing_test()
