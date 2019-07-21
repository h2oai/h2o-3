import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
from random import randint
import re


def glrm_mojo():
    h2o.remove_all()
    NTESTROWS = 200    # number of test dataset rows
    df = pyunit_utils.random_dataset("regression", seed=1234)       # generate random dataset
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    x = df.names
    tol=1e-8

    transform_types = ["NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE"]
    transformN = transform_types[randint(0, len(transform_types)-1)]

    # build a GLRM model with random dataset generated earlier
    glrmModel = H2OGeneralizedLowRankEstimator(k=3, transform=transformN, max_iterations=10, seed=1234)
    glrmModel.train(x=x, training_frame=train)
    glrmTrainFactor = h2o.get_frame(glrmModel._model_json['output']['representation_name'])

    assert glrmTrainFactor.nrows==train.nrows, \
        "X factor row number {0} should equal training row number {1}.".format(glrmTrainFactor.nrows, train.nrows)
    save_GLRM_mojo(glrmModel) # ave mojo model

    MOJONAME = pyunit_utils.getMojoName(glrmModel._id)
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
    h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(glrmModel, TMPDIR, MOJONAME, glrmReconstruct=True) # save mojo predict

    h2o.save_model(glrmModel, TMPDIR)   # save GLRM model
    glrmModel2 = h2o.load_model(os.path.join(TMPDIR,MOJONAME))
    predict_model = glrmModel2.predict(test)
    for col in range(pred_h2o.ncols):
        if pred_h2o[col].isfactor():
            pred_h2o[col] = pred_h2o[col].asnumeric()
            predict_model[col] = predict_model[col].asnumeric()
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 1, tol=tol)
    print("Comparing mojo predict and h2o predict from saved model...")
    pyunit_utils.compare_frames_local(pred_mojo, predict_model, 1, tol=tol)
    frameID, mojoXFactor = pyunit_utils.mojo_predict(glrmModel, TMPDIR, MOJONAME, glrmReconstruct=False) # save mojo XFactor
    glrmTestFactor = h2o.get_frame("GLRMLoading_"+frameID)   # store the x Factor for new test dataset
    print("Comparing mojo x Factor and model x Factor ...")
    pyunit_utils.compare_frames_local(glrmTestFactor, mojoXFactor, 1, tol=tol)

def save_GLRM_mojo(model):
    # save model
    regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
    MOJONAME = regex.sub("_", model._id)

    print("Downloading Java prediction model code from H2O")
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
    os.makedirs(TMPDIR)
    model.download_mojo(path=TMPDIR)    # save mojo


if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_mojo)
else:
    glrm_mojo()
