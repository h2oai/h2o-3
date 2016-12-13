from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
try:
    from StringIO import StringIO   # for python 3
except ImportError:
    from io import StringIO         # for python 2
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.utils.typechecks import assert_is_type


def h2oshow_progress():
    """
    Python API test: h2o.show_progress()

    Command is verified by eyeballing the pyunit test output file and make sure the progress bars are there.
    Here, we will assume the command runs well if there is no error message.
    """
    try:    # only only work with Python 3.
        s = StringIO()
        sys.stdout = s   # redirect output
        h2o.show_progress()   # true by default.
        run_test()
        sys.stdout=sys.__stdout__       # restore old stdout
        # make sure the word progress is found and % is found.  That is how progress is displayed.
        assert ("progress" in s.getvalue()) and ("100%" in s.getvalue()), "h2o.show_progress() command is not working."
    except Exception as e:  # will get error for python 2
        sys.stdout=sys.__stdout__
        assert_is_type(e, AttributeError)   # error for using python 2
        assert "encoding" in e.args[0], "h2o.show_progress() command is not working."
        run_test()  # just run the test and see if there are errors.

def run_test():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
    model.train(x=X, y=Y, training_frame=training_data)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oshow_progress)
else:
    h2oshow_progress()

