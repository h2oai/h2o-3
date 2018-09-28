from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def glrm_svd_init_bug():
    trainData = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    testData = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    glrmModel = H2OGeneralizedLowRankEstimator(k=4, init="svd", svd_method="gram_s_v_d", recover_svd=True)
    glrmModel.train(x=trainData.names, training_frame=trainData)
    predV = glrmModel.predict(testData)

    glrmModel2 = H2OGeneralizedLowRankEstimator(k=4, init="svd", svd_method="power", recover_svd=True)
    glrmModel2.train(x=trainData.names, training_frame=trainData)
    predV2 = glrmModel2.predict(testData)

    glrmModel3 = H2OGeneralizedLowRankEstimator(k=4, init="svd", svd_method="randomized", recover_svd=True)
    glrmModel3.train(x=trainData.names, training_frame=trainData)
    predV3 = glrmModel3.predict(testData)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_svd_init_bug)
else:
    glrm_svd_init_bug()
