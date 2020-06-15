import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from random import randint
import tempfile

def glm_multinomial_mojo_pojo():
    PROBLEM="multinomial"
    NTESTROWS=200
    params = set_params()   # set deeplearning model parameters
    df = pyunit_utils.random_dataset(PROBLEM)       # generate random dataset
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    x = list(set(df.names) - {"response"})
    TMPDIR = tempfile.mkdtemp()
    glmMultinomialModel = pyunit_utils.build_save_model_generic(params, x, train, "response", "glm", TMPDIR) # build and save mojo model
    MOJONAME = pyunit_utils.getMojoName(glmMultinomialModel._id)
    h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(glmMultinomialModel, TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    pred_pojo = pyunit_utils.pojo_predict(glmMultinomialModel, TMPDIR, MOJONAME)
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 0.1, tol=1e-10)    # make sure operation sequence is preserved from Tomk        h2o.save_model(glmOrdinalModel, path=TMPDIR, force=True)  # save model for debugging
    print("Comparing pojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_mojo, pred_pojo, 0.1, tol=1e-10)


def set_params():
    missingValues = ['MeanImputation']
    missing_values = missingValues[randint(0, len(missingValues)-1)]
    params = {'missing_values_handling': missing_values, 'family':"multinomial"}
    print(params)
    return params


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_multinomial_mojo_pojo)
else:
    glm_multinomial_mojo_pojo()
