from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# check checkpointing for Regression with IRLSM.  Checkpointing with single GLM run will not yield same results
# because GLM regression only takes one iteration to complete.  However, it will be equivalent to running
# GLM with different starting coefficients.
def testGLMCheckpointGaussian():
    print("Checking checkpoint for regression....")
    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname]
    myY = "C21"
    myX = list(range(20))
    buildModelCheckpointing(h2o_data, myX, myY, "gaussian", "irlsm")
 

def buildModelCheckpointing(training_frame, x_indices, y_index, family, solver):
    split_frames = training_frame.split_frame(ratios=[0.9], seed=12345)
    modelLong = H2OGeneralizedLinearEstimator(family=family, solver=solver, Lambda=0.5, alpha=0.5) # allow to run to completion
    modelLong.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    model = H2OGeneralizedLinearEstimator(family=family, max_iterations=3, Lambda=0, alpha=0, solver=solver)
    model.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    modelCheckpoint = H2OGeneralizedLinearEstimator(family=family, checkpoint=model.model_id, solver=solver, Lambda=0,
                                                    alpha=0)
    modelCheckpoint.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    assert modelCheckpoint.mse() <= modelLong.mse(), "Checkpoint MSE {0} should be lower than normal model MSE {1} but " \
                                                  "is not!".format(modelCheckpoint.mse(), modelLong.mse())

if __name__ == "__main__":
    pyunit_utils.standalone_test(testGLMCheckpointGaussian)
else:
    testGLMCheckpointGaussian()
