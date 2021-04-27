from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

try:    # redirect python output
    from StringIO import StringIO  # for python 3
except ImportError:
    from io import StringIO  # for python 2

# This test is used to ensured that the correct warning message is received if user tries to use 
# remove_collinear_columns with solver other than IRLSM
def test_GLM_RCC_warning():
    warnNumber = 1
    hdf = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))

    print("Testing for family: TWEEDIE")
    print("Set variables for h2o.")
    y = "CAPSULE"
    x = ["AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON"]

    print("Create models with canonical link: TWEEDIE")
    buffer = StringIO() # redirect output
    sys.stderr=buffer
    model_h2o_tweedie = H2OGeneralizedLinearEstimator(family="tweedie", link="tweedie", alpha=0.5, Lambda=0.1, 
                                                      remove_collinear_columns=True, solver="coordinate_descent")
    model_h2o_tweedie.train(x=x, y=y, training_frame=hdf)   # this should generate a warning message
    model_h2o_tweedie_wo_rcc = H2OGeneralizedLinearEstimator(family="tweedie", link="tweedie", alpha=0.5, Lambda=0.1,
                                                             solver="coordinate_descent")
    sys.stderr=sys.__stderr__   # redirect printout back to normal path
    model_h2o_tweedie_wo_rcc.train(x=x, y=y, training_frame=hdf) # no warning message here.
    
    # since remove_collinear_columns have no effect, this two models should be the same
    pyunit_utils.assertCoefDictEqual(model_h2o_tweedie.coef(), model_h2o_tweedie_wo_rcc.coef())
    
    # check and make sure we get the correct warning message
    warn_phrase = "remove_collinear_columns only works when IRLSM"
    try:        # for python 2.7
        assert len(buffer.buflist)==warnNumber
        print(buffer.buflist[0])
        assert warn_phrase in buffer.buflist[0]
    except:     # for python 3.
        warns = buffer.getvalue()
        print("*** captured warning message: {0}".format(warns))
        assert warn_phrase in warns

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_GLM_RCC_warning)
else:
  test_GLM_RCC_warning()
