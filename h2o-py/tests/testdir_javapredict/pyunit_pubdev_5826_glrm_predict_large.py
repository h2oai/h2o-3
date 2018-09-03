import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
from random import randint
import re

'''
PUBDEV-5826: GLRM model predict and mojo predict differ

During training, we are trying to decompose a dataset A = X*Y.  The X and Y matrices are updated iteratively until
the reconstructed dataset A' converges to A using some metrics.

During prediction/scoring, we try to do A = X'*Y where A and Y are known.  H2O will automatically detect if 
A is the same dataset used to train the model.  If it is, it will automatically return X.  If A is not the same
dataset used during training, we will try to get X' given A and Y.  In this case, we will still use the GLRM framework
of update.  However, we only perform update on X' since Y is already known.

At first glance, we will expect that predict on the training frame and predict on a brand new frame will give 
different results since two different procedures are used.  Here, I am trying to narrow the differences between the
two X generated in this case.

I will use a numerical dataset benign.csv and then work with another dataset that contains both numerical and enum
columns prostate_cat.csv.

The comparisons performed here are:
1. investigate the relationship among rows of the X factor.  Note that we will have two X factors, one from training 
and one from mojo prediction.  If the relationships among the X factor are the same, this means that even if the two
X factors are different, the relationship among the rows have not changed.  We checked two relationships, a. the
direction between two rows (use inner product), b. the distance between two rows (use ||row1-row2||^2).
2. compare the reconstructed datasets from A=X*Y (reconstruction from training X factor) and A=X'*Y (reconstruction
from mojo predict X factor).

'''
def glrm_mojo():
    h2o.remove_all()
    # dataset with numerical values only
    print("Checking GLRM predict performance with numerical dataset.....")
    train = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    get_glrm_xmatrix(train, test, K=3, tol=1e-1)

    # dataset with enum and numerical columns
    print("Checking GLRM predict performance with mixed dataset.....")
    train = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    get_glrm_xmatrix(train, test, K=5, compare_predict=False, tol=1.5)

def get_glrm_xmatrix(train, test, K = 3, compare_predict=True, tol=1e-1):
    x = train.names
    transform_types = ["NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE"]
    transformN = transform_types[randint(0, len(transform_types)-1)]
    print("dataset transform is {0}.".format(transformN))
    # build a GLRM model with random dataset generated earlier
    glrmModel = H2OGeneralizedLowRankEstimator(k=K, transform=transformN, max_iterations=1000, seed=12345)
    glrmModel.train(x=x, training_frame=train)
    glrmTrainFactor = h2o.get_frame(glrmModel._model_json['output']['representation_name'])

    # assert glrmTrainFactor.nrows==train.nrows, \
    #     "X factor row number {0} should equal training row number {1}.".format(glrmTrainFactor.nrows, train.nrows)
    mojoDir = save_GLRM_mojo(glrmModel) # save mojo model

    MOJONAME = pyunit_utils.getMojoName(glrmModel._id)
    h2o.download_csv(test[x], os.path.join(mojoDir, 'in.csv'))  # save test file, h2o predict/mojo use same file

    frameID, mojoXFactor = pyunit_utils.mojo_predict(glrmModel, mojoDir, MOJONAME, glrmReconstruct=False) # save mojo XFactor
    print("Comparing mojo x Factor and model x Factor ...")

    if transformN=="NONE" or not(compare_predict):  # bad performance with no transformation on dataset
        pyunit_utils.check_data_rows(mojoXFactor, glrmTrainFactor, num_rows=mojoXFactor.nrow)
    else:
        pyunit_utils.compare_data_rows(mojoXFactor, glrmTrainFactor, index_list=range(2,mojoXFactor.nrows-1), tol=tol)

    if compare_predict: # only compare reconstructed data frames with numerical data
        pred2 = glrmModel.predict(test) # predict using mojo
        pred1 = glrmModel.predict(train)    # predict using the X from A=X*Y from training

        predictDiff = pyunit_utils.compute_frame_diff(train, pred1)
        mojoDiff = pyunit_utils.compute_frame_diff(train, pred2)
        print("absolute difference of mojo predict and original frame is {0} and model predict and original frame is {1}".format(mojoDiff, predictDiff))


def save_GLRM_mojo(model):
    # save model
    MOJONAME = pyunit_utils.getMojoName(model._id)

    print("Downloading Java prediction model code from H2O")
    tmpdir = os.path.join(pyunit_utils.locate("results"), MOJONAME)
    os.makedirs(tmpdir)
    model.download_mojo(path=tmpdir)    # save mojo
    return tmpdir


if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_mojo)
else:
    glrm_mojo()
