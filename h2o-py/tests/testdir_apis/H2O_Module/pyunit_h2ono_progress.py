from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO

def h2ono_progress():
    """
    Python API test: h2o.no_progress()

    Command is verified by eyeballing the pyunit test output file and make sure the no progress bars are there.
    Here, we will assume the command runs well if there is no error message.
    """
    try:    # only only work with Python 3.
        s = StringIO()
        sys.stdout = s   # redirect output
        h2o.no_progress()   # true by default.
        run_test()
        sys.stdout=sys.__stdout__       # restore old stdout
        # make sure the word progress is found and % is found.  That is how progress is displayed.
        assert s.getvalue()=="", "Nothing should have been printed, instead got " + s.getvalue()
    except Exception as e:  # may get error for python 2
        sys.stdout=sys.__stdout__       # restore old stdout
        assert s.buf=="", "Nothing should have been printed, instead got " + s.buf

def run_test():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
    model.train(x=X, y=Y, training_frame=training_data)


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2ono_progress)
else:
    h2ono_progress()
