from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import pandas
from sklearn import ensemble
from sklearn import preprocessing
from sklearn.metrics import roc_auc_score
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def ecologyGBM():

  ecology_train = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
  ntrees = 100
  max_depth = 5
  min_rows = 10
  learn_rate = 0.1

  # Prepare data for scikit use
  trainData = pandas.read_csv(pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
  trainData.dropna(inplace=True)

  le = preprocessing.LabelEncoder()
  le.fit(trainData['Method'])
  trainData['Method'] = le.transform(trainData['Method'])

  trainDataResponse = trainData["Angaus"]
  trainDataFeatures = trainData[["SegSumT","SegTSeas","SegLowFlow","DSDist","DSMaxSlope","USAvgT",
                                 "USRainDays","USSlope","USNative","DSDam","Method","LocSed"]]


  ecology_train["Angaus"] = ecology_train["Angaus"].asfactor()
  # Train H2O GBM Model:

  gbm_h2o = H2OGradientBoostingEstimator(ntrees=ntrees,
                                         learn_rate=learn_rate,
                                         distribution="bernoulli",
                                         min_rows=min_rows,
                                         max_depth=max_depth,
                                         categorical_encoding='label_encoder')
  gbm_h2o.train(x=list(range(2,ecology_train.ncol)), y="Angaus", training_frame=ecology_train)

  # Train scikit GBM Model:
  gbm_sci = ensemble.GradientBoostingClassifier(learning_rate=learn_rate, n_estimators=ntrees, max_depth=max_depth,
                                                min_samples_leaf=min_rows, max_features=None)
  gbm_sci.fit(trainDataFeatures,trainDataResponse)

  # Evaluate the trained models on test data
  # Load the test data (h2o)
  ecology_test = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_eval.csv"))

  # Load the test data (scikit)
  testData = pandas.read_csv(pyunit_utils.locate("smalldata/gbm_test/ecology_eval.csv"))
  testData.dropna(inplace=True)
  testData['Method'] = le.transform(testData['Method'])

  testDataResponse = testData["Angaus"]
  testDataFeatures = testData[["SegSumT","SegTSeas","SegLowFlow","DSDist","DSMaxSlope","USAvgT",
                               "USRainDays","USSlope","USNative","DSDam","Method","LocSed"]]

  # Score on the test data and compare results

  # scikit
  auc_sci = roc_auc_score(testDataResponse, gbm_sci.predict_proba(testDataFeatures)[:,1])

  # h2o
  gbm_perf = gbm_h2o.model_performance(ecology_test)
  auc_h2o = gbm_perf.auc()

  assert auc_h2o >= auc_sci, "h2o (auc) performance degradation, with respect to scikit"




if __name__ == "__main__":
  pyunit_utils.standalone_test(ecologyGBM)
else:
  ecologyGBM()
