import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import tempfile

# one more test for gam, this time use family fractionalbinomial
def gam_fractional_binomial_mojo_pojo():
    params = set_params()
    train = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/fraction_binommialOrig.csv"))
    test =  h2o.import_file(pyunit_utils.locate("smalldata/glm_test/fraction_binommialOrig.csv"))
    x = ["log10conc"]
    y = "y"

    TMPDIR = tempfile.mkdtemp()
    gamModel = pyunit_utils.build_save_model_generic(params, x, train, y, "gam", TMPDIR) # build and save mojo model
    MOJONAME = pyunit_utils.getMojoName(gamModel._id)

    h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(gamModel, TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 0.1, tol=1e-10)    # make sure operation sequence is preserved from Tomk        h2o.save_model(glmOrdinalModel, path=TMPDIR, force=True)  # save model for debugging

def set_params():
    params = {'family':"fractionalbinomial", 'alpha':[0], 'lambda_':[0],
              'standardize':False, "gam_columns":['log10conc'], "num_knots":[5]}
    return params

if __name__ == "__main__":
    pyunit_utils.standalone_test(gam_fractional_binomial_mojo_pojo)
else:
    gam_fractional_binomial_mojo_pojo()
