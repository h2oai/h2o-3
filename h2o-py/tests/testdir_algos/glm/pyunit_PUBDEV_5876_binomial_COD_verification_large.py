import sys
sys.path.insert(1,"../../../")
import h2o
from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
import os
import subprocess
from subprocess import STDOUT,PIPE

'''
PUBDEV-5876:  Simplify and speed up COD operations.
I train a binomial model with dataset and compare the coefficients at the end of the training to make sure they
agree to the ones before my changes are made.
'''
def test_glm_binomial():
    trainF = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm/binomial_binomial_training_set_enum_trueOneHot.csv.zip"))
    fixInt2Enum(trainF)
    splits = trainF.split_frame(ratios=[0.1, 0.89])
    testF = splits[0]
    y = trainF.ncol-1
    x = list(range(y))
    m_LS = glm(family='binomial', seed=12345, solver="coordinate_descent", max_iterations=10)
    m_LS.train(training_frame=trainF, x=x, y=y)

    mojoZip = pyunit_utils.locate("bigdata/laptop/glm/GLM_model_python_1544561074878_1.zip")
    tmpdir = os.path.realpath('__file__')

    predict_h2o = m_LS.predict(testF)

    # load mojo and have it do predict
    genJarDir = str.split(str(tmpdir),'/')
    genJarDir = '/'.join(genJarDir[0:genJarDir.index('h2o-py')])    # h2o-3
    outFileDir = os.path.join(genJarDir, "h2o-py/tests/results")
    if not(os.path.exists(outFileDir)):
        os.mkdir(outFileDir)
    outFileName = os.path.join(outFileDir, "out_mojo.csv")
    inputFile = os.path.join(outFileDir, "in.csv")
    h2o.download_csv(testF, inputFile)

    java_cmd = ["java", "-ea", "-cp", os.path.join(genJarDir, "h2o-assemblies/genmodel/build/libs/genmodel.jar"),
                "-Xmx12g", "-XX:MaxPermSize=2g", "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv",
                "--input", inputFile, "--output",
                outFileName, "--mojo", mojoZip, "--decimal"]

    p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
    o, e = p.communicate()
    pred_mojo = h2o.import_file(outFileName, header=1)  # load mojo prediction into a frame and compare
    pyunit_utils.compare_frames_local(predict_h2o['p0'], pred_mojo['0'])

def fixInt2Enum(h2oframe):
    numCols = h2oframe.ncol

    for cind in range(numCols):
        ctype = h2oframe.type(cind)
        if ctype=='int':
            h2oframe[cind] = h2oframe[cind].asfactor()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_binomial)
else:
    test_glm_binomial()
