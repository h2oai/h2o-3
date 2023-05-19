from builtins import range
import sys, os
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils

# Michalk has caught problems with glm beta constrains.
def test_glm_beta_constraints_mk():
    df = h2o.import_file(pyunit_utils.locate("smalldata/kaggle/CreditCard/creditcard_train_cat.csv"),
                         col_types={"DEFAULT_PAYMENT_NEXT_MONTH": "enum"})
    lb_limit_bal = 0.0001
    constraints = h2o.H2OFrame({'names':["LIMIT_BAL"], 'lower_bounds': [lb_limit_bal], 'upper_bounds': [1e6]})
    # make sure we have the column names in expected order, the backend does weird things when the order is different    
    constraints = constraints[["names", "lower_bounds", "upper_bounds"]]
    glm_beta = H2OGeneralizedLinearEstimator(model_id="beta_glm", beta_constraints=constraints, seed=42)
    glm_beta.train(y="DEFAULT_PAYMENT_NEXT_MONTH", training_frame=df)
    glm_coeff = glm_beta.coef()
    assert glm_coeff["LIMIT_BAL"] >= lb_limit_bal or glm_coeff["LIMIT_BAL"]==0

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_beta_constraints_mk)
else:
    test_glm_beta_constraints_mk()
