from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# I copied this test from Jeff Plourde.  Thank you.
# This test just needs to run to completion without receiving any error.  There is no assert statement needed here.
def remove_collinear_columns_multinomial():
    train = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_rcc.csv"))
    train[0] = train[0].asfactor()
    mdl = H2OGeneralizedLinearEstimator(solver='IRLSM', family='multinomial', link='family_default', seed=76,
                                        lambda_=[0], max_iterations=100000, beta_epsilon=1e-7, early_stopping=False,
                                        standardize=True, remove_collinear_columns=True)
    mdl.start(x=train.col_names[1:], y=train.col_names[0], training_frame=train)
    mdl.join()
    print("test completed.")

if __name__ == "__main__":
  pyunit_utils.standalone_test(remove_collinear_columns_multinomial)
else:
  remove_collinear_columns_multinomial()
