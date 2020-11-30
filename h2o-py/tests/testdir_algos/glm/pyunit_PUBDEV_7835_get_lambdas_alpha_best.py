import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# This test is written to make sure we can grab the alpha_best, lambda max, lambda min and lambda best values from a 
# glm model
def grab_lambda_values_alpha_best():
    boston = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/BostonHousing.csv"))

    # set the predictor names and the response column name
    predictors = boston.columns[:-1]
    # set the response column to "medv", the median value of owner-occupied homes in $1000's
    response = "medv"

    # convert the chas column to a factor (chas = Charles River dummy variable (= 1 if tract bounds river; 0 otherwise))
    boston['chas'] = boston['chas'].asfactor()

    # split into train and validation sets
    train, valid = boston.split_frame(ratios = [.8], seed=1234)
    boston_glm = glm(lambda_search = True, seed=1234, cold_start=True)
    boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)   
    r = glm.getGLMRegularizationPath(boston_glm)

    assert (glm.getLambdaBest(boston_glm) >= r["lambdas"][len(r["lambdas"])-1]) \
           and (glm.getLambdaBest(boston_glm) <= r["lambdas"][0]), "Error in lambda best extraction"

    assert glm.getLambdaMin(boston_glm) <= r["lambdas"][len(r["lambdas"])-1], "Error in lambda min extraction"
    assert glm.getLambdaMax(boston_glm) == r["lambdas"][0], "Error in lambda max extraction"
    assert glm.getAlphaBest(boston_glm) == boston_glm._model_json['output']['alpha_best'], "Error in alpha best extraction"
    
    boston_glm2 = glm(lambda_search = False, seed=1234)
    boston_glm2.train(x = predictors, y = response, training_frame = train, validation_frame = valid)
    
    try:
        glm.getLambdaMax(boston_glm2)
        assert False, "glm.getLambdaMax(model) should have thrown an error but did not!"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("getLambdaMax(model) can only be called when lambda_search=True" in temp)
        print("grab_lambda_values) test completed!")


if __name__ == "__main__":
    pyunit_utils.standalone_test(grab_lambda_values_alpha_best)
else:
    grab_lambda_values_alpha_best()
