import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def wide_dataset_large():
  print("Reading in Arcene training data for binomial modeling.")
  trainDataResponse = np.genfromtxt(pyunit_utils.locate("smalldata/arcene/arcene_train_labels.labels"), delimiter=' ')
  trainDataResponse = np.where(trainDataResponse == -1, 0, 1)
  trainDataFeatures = np.genfromtxt(pyunit_utils.locate("smalldata/arcene/arcene_train.data"), delimiter=' ')
  xtrain = np.transpose(trainDataFeatures).tolist()
  ytrain = trainDataResponse.tolist()
  trainData = h2o.H2OFrame([ytrain]+xtrain)

  trainData[0] = trainData[0].asfactor()

  print("Run model on 3250 columns of Arcene with strong rules off.")
  model = H2OGeneralizedLinearEstimator(family="binomial", lambda_search=False, alpha=1)
  model.train(x=range(1,3250), y=0, training_frame=trainData)

  print("Test model on validation set.")
  validDataResponse = np.genfromtxt(pyunit_utils.locate("smalldata/arcene/arcene_valid_labels.labels"), delimiter=' ')
  validDataResponse = np.where(validDataResponse == -1, 0, 1)
  validDataFeatures = np.genfromtxt(pyunit_utils.locate("smalldata/arcene/arcene_valid.data"), delimiter=' ')
  xvalid = np.transpose(validDataFeatures).tolist()
  yvalid = validDataResponse.tolist()
  validData = h2o.H2OFrame([yvalid]+xvalid)
  prediction = model.predict(validData)

  print("Check performance of predictions.")
  performance = model.model_performance(validData)

  print("Check that prediction AUC better than guessing (0.5).")
  assert performance.auc() > 0.5, "predictions should be better then pure chance"



if __name__ == "__main__":
  pyunit_utils.standalone_test(wide_dataset_large)
else:
  wide_dataset_large()
