from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import numpy as np

# I copied this test from Jeff Plourde.  Thank you.
# This test just needs to run to completion without receiving any error.  There is no assert statement needed here.
def generate_data(class_centers, sz, seed = 23):
    rng = np.random.default_rng(seed)

    X = np.vstack([rng.normal(cent, 1, size=(sz, 2)) for cent in class_centers])
    X = np.column_stack([X, X.sum(1)])
    y = np.concatenate([np.full(sz, cls) for cls in range(len(class_centers))])
    train = h2o.H2OFrame(np.column_stack([y, X]))
    train[train.col_names[0]] = train[train.col_names[0]].asfactor()
    return train

def remove_collinear_columns_multinomial():
    train = generate_data([0,5,10], 100)
    mdl = H2OGeneralizedLinearEstimator(solver='IRLSM', family='multinomial', link='family_default', seed=76,
                                        lambda_=[0], max_iterations=100000, beta_epsilon=1e-7, early_stopping=False,
                                        standardize=True, remove_collinear_columns=True, max_runtime_secs=30)
    mdl.start(x=train.col_names[1:], y=train.col_names[0], training_frame=train)
    mdl.join()

    mdl2 = H2OGeneralizedLinearEstimator(solver='IRLSM', family='multinomial', link='family_default', seed=76,
                                        lambda_=[0], max_iterations=100000, beta_epsilon=1e-7, early_stopping=False,
                                        standardize=True, remove_collinear_columns=False, max_runtime_secs=30)
    mdl2.start(x=train.col_names[1:], y=train.col_names[0], training_frame=train)
    mdl2.join()
    coefTrue = mdl.coef()
    coefFalse = mdl2.coef()
    assert not(coefTrue['coefs_class_0']['Intercept']==coefFalse['coefs_class_0']['Intercept']), \
        "coefficients should be different but are the same."
    
if __name__ == "__main__":
  pyunit_utils.standalone_test(remove_collinear_columns_multinomial)
else:
  remove_collinear_columns_multinomial()
