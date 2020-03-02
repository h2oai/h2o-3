import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils

def glm_ordinal_mojo_pojo():
    params = set_params()   # set deeplearning model parameters
    train = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/fraction_binommialOrig.csv"))
    test =  h2o.import_file(pyunit_utils.locate("smalldata/glm_test/fraction_binommialOrig.csv"))
    x = ["log10conc"]
    y = "y"

    glmModel = pyunit_utils.build_save_model_GLM(params, x, train, y) # build and save mojo model

    MOJONAME = pyunit_utils.getMojoName(glmModel._id)
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))

    h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(glmModel, TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    pred_pojo = pyunit_utils.pojo_predict(glmModel, TMPDIR, MOJONAME)
    pred_h2o = pred_h2o.drop(3)
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 0.1, tol=1e-10)    # make sure operation sequence is preserved from Tomk        h2o.save_model(glmOrdinalModel, path=TMPDIR, force=True)  # save model for debugging
    print("Comparing pojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_mojo, pred_pojo, 0.1, tol=1e-10)

def set_params():
    params = {'family':"fractionalbinomial", 'alpha':[0], 'lambda_':[0],
              'standardize':False, "compute_p_values":True}
    return params

if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_ordinal_mojo_pojo)
else:
    glm_ordinal_mojo_pojo()
