import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np

def wide_dataset_large():



    print("Reading in Arcene training data for binomial modeling.")
    trainDataResponse = np.genfromtxt(pyunit_utils.locate("smalldata/arcene/arcene_train_labels.labels"), delimiter=' ')
    trainDataResponse = np.where(trainDataResponse == -1, 0, 1)
    trainDataFeatures = np.genfromtxt(pyunit_utils.locate("smalldata/arcene/arcene_train.data"), delimiter=' ')
    trainData = h2o.H2OFrame(zip(*np.column_stack((trainDataResponse, trainDataFeatures)).tolist()))

    print("Run model on 3250 columns of Arcene with strong rules off.")
    model = h2o.glm(x=trainData[1:3250], y=trainData[0].asfactor(), family="binomial", lambda_search=False, alpha=[1])

    print("Test model on validation set.")
    validDataResponse = np.genfromtxt(pyunit_utils.locate("smalldata/arcene/arcene_valid_labels.labels"), delimiter=' ')
    validDataResponse = np.where(validDataResponse == -1, 0, 1)
    validDataFeatures = np.genfromtxt(pyunit_utils.locate("smalldata/arcene/arcene_valid.data"), delimiter=' ')
    validData = h2o.H2OFrame(zip(*np.column_stack((validDataResponse, validDataFeatures)).tolist()))
    prediction = model.predict(validData)

    print("Check performance of predictions.")
    performance = model.model_performance(validData)

    print("Check that prediction AUC better than guessing (0.5).")
    assert performance.auc() > 0.5, "predictions should be better then pure chance"



if __name__ == "__main__":
    pyunit_utils.standalone_test(wide_dataset_large)
else:
    wide_dataset_large()
