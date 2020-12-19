from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# Verify checkpointing for binomial with IRLSM with lambda_search
def testGLMCheckpointBinomialLambdaSearch():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    for ind in range(10):
        train[ind] = train[ind].asfactor()
    train["C21"] = train["C21"].asfactor()
    Y = "C21"
    X = list(range(0,20))
    solver = "irlsm"
    print("Checking checkpoint for binomials with solver {0} and lambda_search".format(solver))
    print("setting cold_start to false")
    buildModelCheckpointing(train, X, Y, "binomial", solver, False, 10) # without cold start
    print("setting cold_start to true")
    buildModelCheckpointing(train, X, Y, "binomial", solver, True, 10) # with cold start

def buildModelCheckpointing(training_frame, x_indices, y_index, family, solver, cold_start, nlambdas):
    split_frames = training_frame.split_frame(ratios=[0.9], seed=12345)
    model = H2OGeneralizedLinearEstimator(family=family, max_iterations=3, solver=solver, lambda_search=True, 
                                          cold_start=cold_start, nlambdas=nlambdas)
    model.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    modelCheckpoint = H2OGeneralizedLinearEstimator(family=family, checkpoint=model.model_id, solver=solver, 
                                                    lambda_search=True, cold_start=cold_start, nlambdas=nlambdas)
    modelCheckpoint.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    # allow to run to completion
    modelLong = H2OGeneralizedLinearEstimator(family=family, solver=solver, lambda_search=True, cold_start=cold_start,
                                              nlambdas=nlambdas) 
    modelLong.train(training_frame=split_frames[0], x=x_indices, y=y_index, validation_frame=split_frames[1])
    
    pyunit_utils.assertEqualCoeffDicts(modelCheckpoint.coef(), modelLong.coef(), tol=1e-6)

if __name__ == "__main__":
    pyunit_utils.standalone_test(testGLMCheckpointBinomialLambdaSearch)
else:
    testGLMCheckpointBinomialLambdaSearch()
