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
        gbm_model.vcov()
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
        h2oglm_vcov.vcov()
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
    vcov_intercept = vcov['intercept']
    vcov_displacement = vcov['displacement']
    vcov_power = vcov['power']
    vcov_weight = vcov['weight']
    vcov_acceleration = vcov['acceleration']
    vcov_year = vcov['year']
    
    print("variance-covariance table: {0}".format(vcov))
  
    # manually obtain covariances and compare with ones using functions
    names = h2oglm_compute_vcov._model_json["output"]["vcov_table"]["names"]
    manual_intercept = h2oglm_compute_vcov._model_json["output"]["vcov_table"]["intercept"]
    manual_displacement = h2oglm_compute_vcov._model_json["output"]["vcov_table"]["displacement"]
    manual_power = h2oglm_compute_vcov._model_json["output"]["vcov_table"]["power"]
    manual_weight = h2oglm_compute_vcov._model_json["output"]["vcov_table"]["weight"]
    manual_acceleration = h2oglm_compute_vcov._model_json["output"]["vcov_table"]["acceleration"]
    manual_year = h2oglm_compute_vcov._model_json["output"]["vcov_table"]["year"]
  
    # check both sets of values are equal
    for i, el in enumerate(names):
        assert abs(vcov_intercept[i]-manual_intercept[i]) < 1e-12, "Expected covariance between intercept and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different".format(
                                                                  vcov_intercept[count], manual_intercept[count])
        assert abs(vcov_displacement[i]-manual_displacement[i]) < 1e-12, "Expected covariance between displacement and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different".format(
                                                                  vcov_displacement[count], manual_displacement[count])
        assert abs(vcov_power[i]-manual_power[i]) < 1e-12, "Expected covariance between power and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different".format(
                                                                  vcov_power[count], manual_power[count])
        assert abs(vcov_weight[i]-manual_weight[i]) < 1e-12, "Expected covariance between weight and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different".format(
                                                                  vcov_weight[count], manual_weight[count])
        assert abs(vcov_acceleration[i]-manual_acceleration[i]) < 1e-12, "Expected covariance between acceleration and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different".format(
                                                                  vcov_acceleration[count], manual_acceleration[count])
        assert abs(vcov_year[i]-manual_year[i]) < 1e-12, "Expected covariance between year and {el} : {0} " \
                                                                  ", actual covariance: {1}.  They are different".format(
                                                                  vcov_year[count], manual_year[count])

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_vcov)
    pyunit_utils.standalone_test(test_wrong_model_vcov)
    pyunit_utils.standalone_test(test_glm_vcov_values)
else:
    test_glm_vcov()
    test_wrong_model_vcov()
    test_glm_vcov_values()
