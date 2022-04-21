from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# test to make sure we have error when compute_p_value=False and coef_with_p_values is called
def test_wrong_model_pzvalues_stdErr():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    y = "economy_20mpg"
    predictors = ["displacement", "power", "weight", "acceleration", "year"]
    cars[y] = cars[y].asfactor()

    h2oglm_compute_p_value = H2OGeneralizedLinearEstimator(family="binomial", seed=1234)
    h2oglm_compute_p_value.train(x=predictors, y=y, training_frame=cars)

    try:
        h2oglm_compute_p_value.coef_with_p_values()
    except ValueError as e:
        assert "p-values, z-values and std_error are not found in model" in e.args[0], "Wrong error messages received."


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_wrong_model_pzvalues_stdErr)
else:
    test_wrong_model_pzvalues_stdErr()
