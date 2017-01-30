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
import inspect


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
        training_data = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
        Y = 3
        X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
        model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
        model.train(x=X, y=Y, training_frame=training_data)
        sys.stdout=sys.__stdout__       # restore old stdout
        # make sure the word progress is found and % is found.  That is how progress is displayed.
        assert ("progress" in s.getvalue()) and ("100%" in s.getvalue()), "h2o.show_progress() command is not working."
    except Exception as e:  # will get error for python 2
        sys.stdout=sys.__stdout__       # restore old stdout
        assert_is_type(e, AttributeError)   # error for using python 2
        assert "encoding" in e.args[0], "h2o.show_progress() command is not working."
        allargs = inspect.getargspec(h2o.show_progress)
        assert len(allargs.args)==0, "h2o.show_progress() should have no arguments!"

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oshow_progress)
else:
    h2oshow_progress()

