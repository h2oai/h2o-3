from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


# make sure error is thrown when non glm model try to call coef_with_p_values
def test_glm_pvalues_stderr():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    y = "economy_20mpg"
    predictors = ["displacement", "power", "weight", "acceleration", "year"]
    cars[y] = cars[y].asfactor()

    gbm_model = H2OGradientBoostingEstimator(distribution="bernoulli", seed=1234)
    gbm_model.train(x=predictors, y=y, training_frame=cars)

    try:
        gbm_model.coef_with_p_values()
    except ValueError as e:
        assert "p-values, z-values and std_error are only found in GLM" in e.args[0], "Wrong error messages received."

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_pvalues_stderr)
else:
    test_glm_pvalues_stderr()
