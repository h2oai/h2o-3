from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# check checkpointing for Multinomial with IRLSM.
def testGLMCheckpointMultinomialLambdaSearch():
    print("Checking checkpoint for multinomials with lambda_search....")
    train = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    train["C1"] = train["C1"].asfactor()
    train["C2"] = train["C2"].asfactor()
    train["C3"] = train["C3"].asfactor()
    train["C4"] = train["C4"].asfactor()
    train["C5"] = train["C5"].asfactor()
    train["C11"] = train["C11"].asfactor()
    myY = "C11"
    mX = list(range(0,10))
    print("Checking checkpoint for multinomials with lambda_search and without cold_start")
    buildModelCheckpointing(train, mX, myY, "multinomial", "irlsm", False, 10) # with cold_start 
    print("Checking checkpoint for multinomials with lambda_search and cold_start")
    buildModelCheckpointing(train, mX, myY, "multinomial", "irlsm", True, 10) # with cold_start 

def buildModelCheckpointing(training_frame, x_indices, y_index, family, solver, cold_start, nlambdas):
    split_frames = training_frame.split_frame(ratios=[0.9], seed=12345)
    model = H2OGeneralizedLinearEstimator(family=family, max_iterations=3, solver=solver, lambda_search=True, 
                                          cold_start=cold_start, nlambdas = nlambdas)
    model.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    modelCheckpoint = H2OGeneralizedLinearEstimator(family=family, checkpoint=model.model_id, solver=solver, 
                                                    lambda_search=True, cold_start=cold_start, nlambdas = nlambdas)
    modelCheckpoint.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    # allow to run to completion
    modelLong = H2OGeneralizedLinearEstimator(family=family, solver=solver, lambda_search=True, cold_start=cold_start,
                                              nlambdas = nlambdas)
    modelLong.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    
    checkpointCoef = modelCheckpoint.coef()
    longCoef = modelLong.coef()
    for key in longCoef.keys():
        pyunit_utils.assertEqualCoeffDicts(checkpointCoef[key], longCoef[key], tol=1e-6)


if __name__ == "__main__":
    pyunit_utils.standalone_test(testGLMCheckpointMultinomialLambdaSearch)
else:
    testGLMCheckpointMultinomialLambdaSearch()
