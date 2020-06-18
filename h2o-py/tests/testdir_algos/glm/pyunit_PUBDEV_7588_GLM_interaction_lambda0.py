from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

from tests import pyunit_utils

# Wzhen Lambda=0, the use_all_factor_levels is automatically set to False.  This runs into the bug of
# interaction vector domain creation.
def test_interaction_Lambda0():
    data = h2o.import_file("https://raw.githubusercontent.com/guru99-edu/R-Programming/master/adult.csv")
    data['y'] = data['hours-per-week'] / data['age']
    
    model_cust = H2OGeneralizedLinearEstimator(interactions = ["income", "gender"],
                                           family = "tweedie",
                                           tweedie_variance_power = 1.7, tweedie_link_power = 0,
                                           Lambda = 0,
                                           intercept = True,
                                           compute_p_values = True,
                                           remove_collinear_columns = True,
                                           standardize = True, weights_column = "age", solver="IRLSM")
    model_cust.train(x = ["income", "gender"], y = "y", training_frame = data)
    coef = len(model_cust.coef())
    expected_coeff_len = 1+1+1+1 # 1 for gender, 1 for income, 1 for gender_income interaction and 1 for intercept
    assert coef == expected_coeff_len, "Expected coefficient length: {0}, actual: {1} and they are different.".format(expected_coeff_len, coef)

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_interaction_Lambda0)
else:
  test_interaction_Lambda0()
