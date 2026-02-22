import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# make sure error is thrown when non glm model try to call vcov
def test_glm_vcov():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    y = "economy_20mpg"
    predictors = ["displacement", "power", "weight", "acceleration", "year"]
    cars[y] = cars[y].asfactor()

    gbm_model = H2OGradientBoostingEstimator(distribution="bernoulli", seed=1234)
    gbm_model.train(x=predictors, y=y, training_frame=cars)

    try:
        vcov = gbm_model.vcov()
    except ValueError as e:
        assert "The variance-covariance matrix is only found in GLM." in e.args[0], "Wrong error messages received."

# test to make sure we have error when compute_p_value=False and vcov is called
def test_wrong_model_vcov():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    y = "economy_20mpg"
    predictors = ["displacement", "power", "weight", "acceleration", "year"]
    cars[y] = cars[y].asfactor()

    h2oglm_vcov = H2OGeneralizedLinearEstimator(family="binomial", seed=1234)
    h2oglm_vcov.train(x=predictors, y=y, training_frame=cars)

    try:
        vcov = h2oglm_vcov.vcov()
    except ValueError as e:
        assert "The variance-covariance matrix is only calculated when compute_p_values=True" in e.args[0], "Wrong error messages received."

# make sure correct covariances are returned when vcov is called
def test_glm_vcov_values():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    y = "economy_20mpg"
    predictors = ["displacement","power","weight","acceleration","year"]
    cars[y] = cars[y].asfactor()

    h2oglm_compute_vcov =  H2OGeneralizedLinearEstimator(family = "binomial", lambda_=0.0, compute_p_values=True, 
                                                          seed = 1234)

    h2oglm_compute_vcov.train(x = predictors, y = y, training_frame  = cars)
    vcov = h2oglm_compute_vcov.vcov()
    vcov_intercept = vcov['Intercept']
    vcov_displacement = vcov['displacement']
    vcov_power = vcov['power']
    vcov_weight = vcov['weight']
    vcov_acceleration = vcov['acceleration']
    vcov_year = vcov['year']
    
    print("variance-covariance table: {0}".format(vcov))
  
    # manually obtain covariances and compare with ones using functions
    hf_vcov = h2o.get_frame(h2oglm_compute_vcov._model_json["output"]["vcov_table"]["name"])
    names = hf_vcov["Names"]
    manual_intercept = hf_vcov["Intercept"]
    manual_displacement = hf_vcov["displacement"]
    manual_power = hf_vcov["power"]
    manual_weight = hf_vcov["weight"]
    manual_acceleration = hf_vcov["acceleration"]
    manual_year = hf_vcov["year"]
  
    # check both sets of values are equal
    for i in range(names.shape[0]):
        el = names[i,0]
        assert abs(vcov_intercept[i, 0]-manual_intercept[i, 0]) < 1e-12, f"Expected covariance between Intercept and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different.".format(
                                                                  vcov_intercept[i, 0], manual_intercept[i, 0])
        assert abs(vcov_displacement[i, 0]-manual_displacement[i, 0]) < 1e-12, f"Expected covariance between displacement and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different.".format(
                                                                  vcov_displacement[i, 0], manual_displacement[i, 0])
        assert abs(vcov_power[i, 0]-manual_power[i, 0]) < 1e-12, f"Expected covariance between power and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different.".format(
                                                                  vcov_power[i, 0], manual_power[i, 0])
        assert abs(vcov_weight[i, 0]-manual_weight[i, 0]) < 1e-12, f"Expected covariance between weight and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different.".format(
                                                                  vcov_weight[i, 0], manual_weight[i, 0])
        assert abs(vcov_acceleration[i, 0]-manual_acceleration[i, 0]) < 1e-12, f"Expected covariance between acceleration and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different.".format(
                                                                  vcov_acceleration[i, 0], manual_acceleration[i, 0])
        assert abs(vcov_year[i, 0]-manual_year[i, 0]) < 1e-12, f"Expected covariance between year and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different.".format(
                                                                  vcov_year[i, 0], manual_year[i, 0])

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_vcov)
    pyunit_utils.standalone_test(test_wrong_model_vcov)
    pyunit_utils.standalone_test(test_glm_vcov_values)
else:
    test_glm_vcov()
    test_wrong_model_vcov()
    test_glm_vcov_values()
