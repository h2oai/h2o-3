import sys
import tempfile
import shutil
import time
import os

import pandas

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# In this test, we train simple glm regression model with 1 predictor only.
# Next, we call h2o.mojo_predict_csv with test data where a row is replaced with a string a.
# In this case, the predicted value should be the same as if we set the predictor to the mean of the column.
# testdataModel has the illegal string replaced with mean of the column.
#
# The mojo predict and model predict should in this case render the same results.  We check this at the end of the
# test.
def test_setInvNumNA():
    train = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/pubdev_6617_setInvNumNA_train.csv"))
    testdata = pyunit_utils.locate("smalldata/glm_test/pubdev_6617_setInvNumNA_test.csv")
    testdataModel = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/pubdev_6617_setInvNumNA_test_model.csv"))

    response = "C2"
    x = ["C1"]
    params = {'missing_values_handling':"MeanImputation", 'family':'gaussian'}
    glmMultinomialModel = pyunit_utils.build_save_model_GLM(params, x, train, response) # build and save mojo model

    MOJONAME = pyunit_utils.getMojoName(glmMultinomialModel._id)
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
    mojoLoco = os.path.normpath(os.path.join(TMPDIR, MOJONAME+'.zip'))
    mojoOut = os.path.normpath(os.path.join(TMPDIR, "mojo_out.csv"))
    genJarDir = str.split(str(TMPDIR ),'/')
    genJarDir = '/'.join(genJarDir[0:genJarDir.index('h2o-py')])    # locate directory of genmodel.jar
    jarpath = os.path.join(genJarDir, "h2o-assemblies/genmodel/build/libs/genmodel.jar")
    mojoPredict = h2o.mojo_predict_csv(input_csv_path=testdata, mojo_zip_path=mojoLoco, output_csv_path=mojoOut, 
                                       genmodel_jar_path=jarpath, verbose=True, setInvNumNA=True)
    modelPred = glmMultinomialModel.predict(testdataModel)
    for ind in range(5):
        assert abs(float(mojoPredict[ind]['predict'])-modelPred[ind,0])<1e-6, "model predict {1} and mojo predict {0} differs " \
                                                                            "too much".format(float(mojoPredict[0]['predict']), modelPred[ind,0])

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_setInvNumNA)
else:
    test_setInvNumNA()
