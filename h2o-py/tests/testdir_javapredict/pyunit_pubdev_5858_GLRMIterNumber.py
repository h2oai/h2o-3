import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
from random import randint
import re
import time
import subprocess
from subprocess import STDOUT,PIPE


def glrm_mojo():
    h2o.remove_all()
    NTESTROWS = 200    # number of test dataset rows
    df = pyunit_utils.random_dataset("regression", seed=1234)       # generate random dataset
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    x = df.names

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
    # test and make sure setting the iteration number did not screw up the prediction
    predID, pred_mojo = pyunit_utils.mojo_predict(glrmModel, TMPDIR, MOJONAME, glrmIterNumber=100) # save mojo predict
    pred_h2o = h2o.get_frame("GLRMLoading_"+predID)
    print("Comparing mojo x Factor and model x Factor for 100 iterations")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 1, tol=1e-10)
    
    # scoring with 2 iterations should be shorter than scoring with 8000 iterations
    starttime = time.time()
    runMojoPredictOnly(TMPDIR, MOJONAME, glrmIterNumber=8000) # save mojo predict
    time1000 = time.time()-starttime
    starttime = time.time()
    runMojoPredictOnly(TMPDIR, MOJONAME, glrmIterNumber=2) # save mojo predict
    time10 = time.time()-starttime
    print("Time taken for 2 iterations: {0}s.  Time taken for 8000 iterations: {1}s.".format(time10, time1000))

def save_GLRM_mojo(model):
    # save model
    regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
    MOJONAME = regex.sub("_", model._id)

    print("Downloading Java prediction model code from H2O")
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
    os.makedirs(TMPDIR)
    model.download_mojo(path=TMPDIR)    # save mojo
    return TMPDIR

def runMojoPredictOnly(tmpdir, mojoname, glrmIterNumber=100):
    outFileName = os.path.join(tmpdir, 'out_mojo.csv')
    mojoZip = os.path.join(tmpdir, mojoname) + ".zip"
    genJarDir = str.split(str(tmpdir),'/')
    genJarDir = '/'.join(genJarDir[0:genJarDir.index('h2o-py')])    # locate directory of genmodel.jar

    java_cmd = ["java", "-ea", "-cp", os.path.join(genJarDir, "h2o-assemblies/genmodel/build/libs/genmodel.jar"),
                "-Xmx12g", "-XX:MaxPermSize=2g", "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv",
                "--input", os.path.join(tmpdir, 'in.csv'), "--output",
                outFileName, "--mojo", mojoZip, "--decimal"]
    java_cmd.append("--glrmIterNumber")
    java_cmd.append(str(glrmIterNumber))

    p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
    o, e = p.communicate()

if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_mojo)
else:
    glrm_mojo()
