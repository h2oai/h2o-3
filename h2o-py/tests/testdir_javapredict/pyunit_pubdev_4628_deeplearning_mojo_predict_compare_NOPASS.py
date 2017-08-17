from builtins import range
import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import subprocess
from subprocess import STDOUT,PIPE

NTESTROWS = 1000    # number of test dataset rows
MAXLAYERS = 8
MAXNODESPERLAYER = 20

def deeplearning_mojo():

    modelP = h2o.load_model("/Users/wendycwong/h2o-3/h2o-py/tests/results/DeepLearning_model_python_1503957512805_4/DeepLearning_model_python_1503957512805_4")
    testdata = h2o.import_file("/Users/wendycwong/h2o-3/h2o-py/tests/results/DeepLearning_model_python_1503957512805_4/in.csv")
    h2oOut = modelP.predict(testdata)
    h2o.download_csv(h2oOut, "/Users/wendycwong/h2o-3/h2o-py/tests/results/DeepLearning_model_python_1503957512805_4/out_h2o.csv")
    # generate prediction from mojo
    java_cmd = ["java", "-ea", "-cp", "/Users/wendycwong/h2o-3/h2o-assemblies/genmodel/build/libs/genmodel.jar" , "-Xmx12g", "-XX:MaxPermSize=2g",
                "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv",
                "--model", "/Users/wendycwong/h2o-3/h2o-py/tests/results/DeepLearning_model_python_1503957512805_4/DeepLearning_model_python_1503957512805_4.zip",
                "--input", "/Users/wendycwong/h2o-3/h2o-py/tests/results/DeepLearning_model_python_1503957512805_4/in_csv",
                "--output", "/Users/wendycwong/h2o-3/h2o-py/tests/results/DeepLearning_model_python_1503957512805_4/out_pojo.csv", "--decimal"]


    p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
    o, e = p.communicate()
    mojoOut = h2o.import_file("/Users/wendycwong/h2o-3/h2o-py/tests/results/DeepLearning_model_python_1503957512805_4/out_pojo.csv")
    print("Wow")

if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_mojo)
else:
    deeplearning_mojo()
