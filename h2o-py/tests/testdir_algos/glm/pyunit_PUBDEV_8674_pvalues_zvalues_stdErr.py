from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# make sure correct p-values, z-values, std_errors are returned when coef_with_p_values is called
def test_glm_pvalues_stderr():
  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
  y = "economy_20mpg"
  predictors = ["displacement","power","weight","acceleration","year"]
  cars[y] = cars[y].asfactor()

  h2oglm_compute_p_value =  H2OGeneralizedLinearEstimator(family = "binomial", lambda_=0.0, compute_p_values=True, 
                                                          seed = 1234)

  h2oglm_compute_p_value.train(x = predictors, y = y, training_frame  = cars)
  coef_w_p_values = h2oglm_compute_p_value.coef_with_p_values()
  coef_p_values = coef_w_p_values["p_value"]
  coef_z_values = coef_w_p_values["z_value"]
  coef_std_err = coef_w_p_values["std_error"]
  print("coefficient table with p_values: {0}".format(coef_w_p_values))
  
  # manually obtain p_values, z_values and std_err and compare with ones using functions
  names = h2oglm_compute_p_value._model_json["output"]["coefficients_table"]["names"]
  manual_pvalue = h2oglm_compute_p_value._model_json["output"]["coefficients_table"]["p_value"]
  manual_zvalue = h2oglm_compute_p_value._model_json["output"]["coefficients_table"]["z_value"]
  manual_stderr = h2oglm_compute_p_value._model_json["output"]["coefficients_table"]["std_error"]
  
  # check both sets of values are equal
  for count in range(len(names)):
      assert abs(coef_p_values[count]-manual_pvalue[count]) < 1e-12, "Expected p-value: {0}, actual p-value: {1}.  They are " \
                                                              "different".format(coef_p_values[count], manual_pvalue[count])
      assert abs(coef_z_values[count]-manual_zvalue[count]) < 1e-12, "Expected z-value: {0}, actual z-value: {1}.  They are " \
                                                              "different".format(coef_z_values[count], manual_zvalue[count])
      assert abs(coef_std_err[count]-manual_stderr[count]) < 1e-12, "Expected std_err: {0}, actual std_err: {1}.  They are " \
                                                              "different".format(coef_std_err[count], manual_stderr[count])
      
if __name__ == "__main__":
  pyunit_utils.standalone_test(test_glm_pvalues_stderr)
else:
  test_glm_pvalues_stderr()

