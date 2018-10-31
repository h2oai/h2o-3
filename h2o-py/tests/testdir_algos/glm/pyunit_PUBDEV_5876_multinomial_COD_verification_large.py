import sys
sys.path.insert(1,"../../../")
import h2o
from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
import os
import subprocess
from subprocess import STDOUT,PIPE

def test_glm_multinomial():
    trainF = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm/multinomial20Class_10KRows.csv"))
    fixInt2Enum(trainF)
    splits = trainF.split_frame(ratios=[0.1, 0.89])
    testF = splits[0]
    y = trainF.ncol-1
    x = list(range(y))
    m_LS = glm(family='multinomial', seed=12345, solver="coordinate_descent", max_iterations=5)
    m_LS.train(training_frame=trainF, x=x, y=y)

    mojoZip = pyunit_utils.locate("bigdata/laptop/glm/GLM_model_python_1543520565753_3.zip")
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
    predNames = predict_h2o.names
    predNames.remove(u'predict')
    mojoNames = pred_mojo.names
    mojoNames.remove(u'predict')
    pyunit_utils.compare_frames_local(predict_h2o[predNames], pred_mojo[mojoNames])

def fixInt2Enum(h2oframe):
    numCols = h2oframe.ncol

    for cind in range(numCols):
        ctype = h2oframe.type(cind)
        if ctype=='int':
            h2oframe[cind] = h2oframe[cind].asfactor()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_multinomial)
else:
    test_glm_multinomial()
