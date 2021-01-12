from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# check checkpointing for Regression with IRLSM.
def testGLMCheckpointGaussianLambdaSearch():
    print("Checking checkpoint for regression with lambda search ....")
    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname]
    myY = "C21"
    myX = list(range(20))
    print("Setting cold_start to false")
    buildModelCheckpointing(h2o_data, myX, myY, "gaussian", "irlsm", False)
    print("Setting cold_start to true")
    buildModelCheckpointing(h2o_data, myX, myY, "gaussian", "irlsm", True)

 

def buildModelCheckpointing(training_frame, x_indices, y_index, family, solver, cold_start):
    split_frames = training_frame.split_frame(ratios=[0.9], seed=12345)
    model = H2OGeneralizedLinearEstimator(family=family, max_iterations=3, solver=solver, lambda_search=True, 
                                          cold_start=cold_start)
    model.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    modelCheckpoint = H2OGeneralizedLinearEstimator(family=family, checkpoint=model.model_id, solver=solver, 
                                                    lambda_search=True, cold_start=cold_start)
    modelCheckpoint.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    modelLong = H2OGeneralizedLinearEstimator(family=family, solver=solver, lambda_search=True, cold_start=cold_start)
    modelLong.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])

    pyunit_utils.assertEqualCoeffDicts(modelCheckpoint.coef(), modelLong.coef(), tol=1e-6)

if __name__ == "__main__":
    pyunit_utils.standalone_test(testGLMCheckpointGaussianLambdaSearch)
else:
    testGLMCheckpointGaussianLambdaSearch()
