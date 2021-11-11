from __future__ import print_function
from builtins import range
import sys, os
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils

# This test is to test our implementation of allowing users to specify beta constraints using python dicts instead of
# H2O frame.  The user will specify a lower and upper bound for each variable she wants to impose bounds on using 
# python dicts and using H2OFrame.  Model built with both ways should generate the same coefficients.
def test_glm_beta_constraints_dict_megan():
    df = h2o.import_file(pyunit_utils.locate("smalldata/kaggle/CreditCard/creditcard_train_cat.csv"),
                         col_types={"DEFAULT_PAYMENT_NEXT_MONTH": "enum"})
    lb_limit_bal = 0.0001
    constraints = h2o.H2OFrame({'names':["LIMIT_BAL", "AGE"], 'lower_bounds': [lb_limit_bal, lb_limit_bal], 
                                'upper_bounds': [1e6, 1e6]})
    # make sure we have the column names in expected order, the backend does weird things when the order is different    
    constraints = constraints[["names", "lower_bounds", "upper_bounds"]]
    glm_beta = H2OGeneralizedLinearEstimator(model_id="beta_glm", beta_constraints=constraints, seed=42)
    glm_beta.train(y="DEFAULT_PAYMENT_NEXT_MONTH", training_frame=df)
    glm_coeff = glm_beta.coef()
    assert glm_coeff["LIMIT_BAL"] >= lb_limit_bal or glm_coeff["LIMIT_BAL"]==0
    # using dict for beta_constraints
    constraints2 = {"LIMIT_BAL":{"lower_bound":lb_limit_bal, "upper_bound":1e6}, "AGE":{"lower_bound":lb_limit_bal,
                                                                                        "upper_bound":1e6}}
    glm_beta_dict = H2OGeneralizedLinearEstimator(model_id="beta_glm", beta_constraints=constraints2, seed=42)
    glm_beta_dict.train(y="DEFAULT_PAYMENT_NEXT_MONTH", training_frame=df)
    glm_coeff_dict = glm_beta_dict.coef()
    pyunit_utils.assertCoefDictEqual(glm_coeff, glm_coeff_dict, tol=1e-6) # coefficients should be the same from both runs
    print("test complete!")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_beta_constraints_dict_megan)
else:
    test_glm_beta_constraints_dict_megan()
