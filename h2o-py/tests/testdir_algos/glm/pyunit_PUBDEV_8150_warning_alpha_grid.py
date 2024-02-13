from builtins import range
import contextlib
from io import StringIO
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch

# This test is used to make sure when a user tries to set alpha in the hyper-parameter of gridsearch, a warning
# should appear to tell the user to set the alpha array as an parameter in the algorithm.
def grid_alpha_search():
    warnNumber = 1
    hdf = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))

    print("Testing for family: TWEEDIE")
    print("Set variables for h2o.")
    y = "CAPSULE"
    x = ["AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON"]

    hyper_parameters = {'alpha': [0, 0.5]}    # set hyper_parameters for grid search

    print("Create models with lambda_search")
    err = StringIO() 
    with contextlib.redirect_stderr(err):
        model_h2o_grid_search = H2OGridSearch(H2OGeneralizedLinearEstimator(family="tweedie", Lambda=0.5),
                                              hyper_parameters)
        model_h2o_grid_search.train(x=x, y=y, training_frame=hdf)

    # check and make sure we get the correct warning message
    warn_phrase = "Adding alpha array to hyperparameter runs slower with gridsearch."
    warns = err.getvalue()
    print("*** captured warning message: {0}".format(warns))
    assert warn_phrase in warns
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_alpha_search)
else:
    grid_alpha_search()
