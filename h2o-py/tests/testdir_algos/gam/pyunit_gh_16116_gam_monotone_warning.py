import sys
import contextlib
from io import StringIO
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator as gam

def test_monotone_spline():
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    
    err = StringIO()
    with contextlib.redirect_stderr(err):    
        gam_models = gam(family = "binomial", gam_columns = ["C12"], bs = [2], seed=1234)
        gam_models.train(x = ["C1", "C2", "C13"], y = myY, training_frame = h2o_data)

    # check and make sure we get the correct warning message
    warn_phrase = "is not set to true when I-spline/monotone-spline (bs=2) is chosen."
    warns = err.getvalue()
    print("*** captured warning message: {0}".format(warns))
    assert warn_phrase in warns    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_monotone_spline)
else:
    test_monotone_spline()
