import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import tempfile

# test GAM TP and CS smoothers for Binomial family
def gam_binomial_mojo():
    params = set_params()
    train = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    test =  h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    train["C21"] = train["C21"].asfactor()
    test["C21"] = test["C21"].asfactor()
    x=["C1"]
    y = "C21"

    TMPDIR = tempfile.mkdtemp()
    gamModel = pyunit_utils.build_save_model_generic(params, x, train, y, "gam", TMPDIR) # build and save mojo model
    MOJONAME = pyunit_utils.getMojoName(gamModel._id)

    h2o.download_csv(test, os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(gamModel, TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 1, tol=1e-10)    # make sure operation sequence is preserved from Tomk        h2o.save_model(glmOrdinalModel, path=TMPDIR, force=True)  # save model for debugging

def set_params():
    params = {'family':"binomial", 'bs':[1,1,1,0],'standardize':False, 
              "gam_columns":[["C12"], ["C13", "C14"], ["C15", "C16", "C17"], ["C18"]]}
    return params

if __name__ == "__main__":
    pyunit_utils.standalone_test(gam_binomial_mojo)
else:
    gam_binomial_mojo()
