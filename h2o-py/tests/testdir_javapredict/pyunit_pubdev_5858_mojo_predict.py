import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
import re

def glrm_mojo():
    h2o.remove_all()
    train = h2o.import_file(pyunit_utils.locate("smalldata/glrm_test/pubdev_5858_glrm_mojo_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/glrm_test/pubdev_5858_glrm_mojo_test.csv"))
    predict_10iter = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glrm_test/pubdev_5858_glrm_predict_10iter.csv")
    predict_1iter = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glrm_test/pubdev_5858_glrm_predict_1iter.csv")

    x = train.names
    transformN = "STANDARDIZE"

    # build a GLRM model with random dataset generated earlier
    glrmModel = H2OGeneralizedLowRankEstimator(k=3, transform=transformN, max_iterations=10, seed=1234, init="random")
    glrmModel.train(x=x, training_frame=train)
    glrmTrainFactor = h2o.get_frame(glrmModel._model_json['output']['representation_name'])

    assert glrmTrainFactor.nrows==train.nrows, \
        "X factor row number {0} should equal training row number {1}.".format(glrmTrainFactor.nrows, train.nrows)
    save_GLRM_mojo(glrmModel) # save mojo model
    MOJONAME = pyunit_utils.getMojoName(glrmModel._id)
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
    h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    # test and make sure setting the iteration number did not screw up the prediction
    predID, pred_mojo = pyunit_utils.mojo_predict(glrmModel, TMPDIR, MOJONAME, glrmIterNumber=100) # save mojo predict
    pred_h2o = h2o.get_frame("GLRMLoading_"+predID)
    assert pred_h2o is not None
    print("Comparing mojo x Factor and model x Factor for 100 iterations")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 1, tol=1e-10)
    predID, pred_mojo = pyunit_utils.mojo_predict(glrmModel, TMPDIR, MOJONAME, glrmIterNumber=1) # save mojo predict
    print("Comparing mojo x Factor and model x Factor for 1 iterations")
    pyunit_utils.compare_frames_local(predict_1iter, pred_mojo, 1, tol=1e-10)
    predID, pred_mojo = pyunit_utils.mojo_predict(glrmModel, TMPDIR, MOJONAME, glrmIterNumber=10) # save mojo predict
    print("Comparing mojo x Factor and model x Factor for 10 iterations")
    pyunit_utils.compare_frames_local(predict_10iter, pred_mojo, 1, tol=1e-10)


def save_GLRM_mojo(model):
    # save model
    regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
    MOJONAME = regex.sub("_", model._id)

    print("Downloading Java prediction model code from H2O")
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
    os.makedirs(TMPDIR)
    model.download_mojo(path=TMPDIR)    # save mojo
    return TMPDIR


if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_mojo)
else:
    glrm_mojo()
