from __future__ import print_function
from builtins import str
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

try:
    from StringIO import StringIO  # for python 3
except ImportError:
    from io import StringIO  # for python 2


def glrm_iris():
    print("Importing iris_wheader.csv data...")
    irisH2O = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    irisTest = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader_bad_cnames.csv"))

    rank = 3
    gx = 0.5
    gy = 0.5
    trans = "STANDARDIZE"
    print("H2O GLRM with rank k = " + str(rank) + ", gamma_x = " + str(gx) + ", gamma_y = " + str(
        gy) + ", transform = " + trans)
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=rank, loss="Quadratic", gamma_x=gx, gamma_y=gy, transform=trans)
    glrm_h2o.train(x=irisH2O.names, training_frame=irisH2O)

    print("Impute original data from XY decomposition")  # and expect warnings
    buffer = StringIO()     # redirect warning messages to string buffer for later analysis
    sys.stderr = buffer

    h2o_pred = glrm_h2o.predict(irisTest)

    warn_phrase = "UserWarning"
    warn_string_of_interest = "missing column"
    sys.stderr = sys.__stderr__     # redirect it back to stdout.
    try:        # for python 2.7
        if len(buffer.buflist) > 0:
            for index in range(len(buffer.buflist)):
                print("*** captured warning message: {0}".format(buffer.buflist[index]))
                assert (warn_phrase in buffer.buflist[index]) and (warn_string_of_interest in buffer.buflist[index])
    except:     # for python 3.
        warns = buffer.getvalue()
        print("*** captured warning message: {0}".format(warns))
        assert (warn_phrase in warns) and (warn_string_of_interest in warns)

if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_iris)
else:
    glrm_iris()
