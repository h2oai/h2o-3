import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
from random import randint
import re
import subprocess
from subprocess import STDOUT,PIPE


def glrm_mojo():
    h2o.remove_all()
    testfile = h2o.import_file("/Users/wendycwong/temp/tmp_model_49942/in.csv")
    x = testfile.names
    modelG = h2o.load_model("/Users/wendycwong/temp/tmp_model_49942/GLRM_model_R_1531173578893_78")
    predval = modelG.predict_leaf_node_assignment(testfile)

    java_cmd = ["java", "-ea", "-cp", "/Users/wendycwong/h2o/h2o-assemblies/genmodel/build/libs/genmodel.jar",
                "-Xmx12g", "-XX:MaxPermSize=2g", "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv",
                "--input", "/Users/wendycwong/temp/tmp_model_49942/in.csv", "--output",
                "/Users/wendycwong/temp/tmp_model_49942/out_mojo_d.csv", "--mojo",
                "/Users/wendycwong/temp/tmp_model_49942/GLRM_model_R_1531173578893_78.zip", "--decimal", "--glrmReconstruct"]

    p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
    o, e = p.communicate()

    pred_mojo = h2o.import_file("/Users/wendycwong/temp/tmp_model_49942/out_mojo_d.csv", header=1)

    pyunit_utils.compare_frames_local(predval, pred_mojo, 1, tol=1e-10)

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
