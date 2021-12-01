from __future__ import print_function
from builtins import str
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

# make sure error was thrown when model_id and representation_name use the same string.
def glrm_iris_error_message():
    print("Importing iris_wheader.csv data...")
    irisH2O = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    rank = 3
    gx = 0.5
    gy = 0.5
    trans = "STANDARDIZE"
    print("H2O GLRM with rank k = " + str(rank) + ", gamma_x = " + str(gx) + ", gamma_y = " + str(
        gy) + ", transform = " + trans)
    try:
        glrm_h2o = H2OGeneralizedLowRankEstimator(k=rank, loss="Quadratic", gamma_x=gx, gamma_y=gy, transform=trans,
                                          model_id="one", representation_name="one")
        glrm_h2o.train(x=irisH2O.names, training_frame=irisH2O)
        assert False, "Should have thrown an exception!"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("representation_name and model_id cannot use the same string" in temp), "Wrong exception was received."

if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_iris_error_message)
else:
    glrm_iris_error_message()
