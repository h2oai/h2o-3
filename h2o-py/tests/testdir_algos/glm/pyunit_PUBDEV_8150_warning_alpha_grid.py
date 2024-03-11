from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch

try:    # redirect python output
    from StringIO import StringIO  # for python 3
except ImportError:
    from io import StringIO  # for python 2

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
    buffer = StringIO() # redirect output
    sys.stderr=buffer
    model_h2o_grid_search = H2OGridSearch(H2OGeneralizedLinearEstimator(family="tweedie", Lambda=0.5),
                                          hyper_parameters)
    model_h2o_grid_search.train(x=x, y=y, training_frame=hdf)
    sys.stderr=sys.__stderr__   # redirect printout back to normal path

    # check and make sure we get the correct warning message
    warn_phrase = "Adding alpha array to hyperparameter runs slower with gridsearch."
    try:        # for python 2.7
        assert len(buffer.buflist)==warnNumber
        print(buffer.buflist[0])
        assert warn_phrase in buffer.buflist[0]
    except:     # for python 3.
        warns = buffer.getvalue()
        print("*** captured warning message: {0}".format(warns))
        assert warn_phrase in warns
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_alpha_search)
else:
    grid_alpha_search()
