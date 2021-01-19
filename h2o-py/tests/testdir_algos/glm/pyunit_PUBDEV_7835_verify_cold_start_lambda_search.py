import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# This test will check when cold start = true, lambda search should produce same coefficients as if you grab each
# lambda value and build the model yourself.

def grab_lambda_min():
    boston = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/BostonHousing.csv"))

    # set the predictor names and the response column name
    predictors = boston.columns[:-1]
    # set the response column to "medv", the median value of owner-occupied homes in $1000's
    response = "medv"

    # convert the chas column to a factor (chas = Charles River dummy variable (= 1 if tract bounds river; 0 otherwise))
    boston['chas'] = boston['chas'].asfactor()

    # split into train and validation sets
    train, valid = boston.split_frame(ratios = [.8], seed=1234)
    boston_glm = H2OGeneralizedLinearEstimator(lambda_search = True, seed=1234, cold_start=True)
    boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)   
    r = H2OGeneralizedLinearEstimator.getGLMRegularizationPath(boston_glm)

    for l in range(0,len(r['lambdas'])):
        m = H2OGeneralizedLinearEstimator(alpha=[r['alphas'][l]],Lambda=r['lambdas'][l],
                                          solver='COORDINATE_DESCENT')
        m.train(x = predictors, y = response, training_frame = train, validation_frame = valid)
        cs = r['coefficients'][l]
        cs_norm = r['coefficients_std'][l]
        print("comparing coefficients for submodel {0}".format(l))
        pyunit_utils.assertEqualCoeffDicts(cs, m.coef(), tol=1e-6)
        pyunit_utils.assertEqualCoeffDicts(cs_norm, m.coef_norm(), tol=1e-6)


if __name__ == "__main__":
    pyunit_utils.standalone_test(grab_lambda_min)
else:
    grab_lambda_min()
