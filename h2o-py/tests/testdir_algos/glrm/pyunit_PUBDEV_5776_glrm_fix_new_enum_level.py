from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

# PUBDEV-5776: GLRM throw error with unseen levels in enum columns
#
# Without my fix, this code will not run to completion.  There are more enum levels in the test data than in
# training data.  Thanks to Nidhi for the original code in R.
#
# Hence, it is okay to not have an assert statement here.

def glrm_catagorical_bug_fix():
    trainData = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    testData = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    glrmModel = H2OGeneralizedLowRankEstimator(k=4)
    glrmModel.train(x=trainData.names, training_frame=trainData)
    predV = glrmModel.predict(testData)
    print(predV)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_catagorical_bug_fix)
else:
    glrm_catagorical_bug_fix()
