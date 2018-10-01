from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def glrm_svd_init_bug():
    trainData = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    testData = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    glrmModel = H2OGeneralizedLowRankEstimator(k=4, loss="huber", init="svd", recover_svd=True)
    glrmModel.train(x=trainData.names, training_frame=trainData)
    #predV = glrmModel.predict(testData)

    y = glrmModel.archetypes()
    print(y)
    x = h2o.get_frame(glrmModel._model_json["output"]["representation_name"])
    print(x)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_svd_init_bug)
else:
    glrm_svd_init_bug()
