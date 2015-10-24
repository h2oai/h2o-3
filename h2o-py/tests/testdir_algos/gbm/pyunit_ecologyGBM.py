import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


import numpy as np
from sklearn import ensemble
from sklearn.metrics import roc_auc_score

def ecologyGBM():
    
    

    #Log.info("Importing ecology_model.csv data...\n")
    ecology_train = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
    #Log.info("Summary of the ecology data from h2o: \n")
    #ecology.summary()

    # Log.info("==============================")
    # Log.info("H2O GBM Params: ")
    # Log.info("x = ecology_train[2:14]")
    # Log.info("y = ecology_train["Angaus"]")
    # Log.info("ntrees = 100")
    # Log.info("max_depth = 5")
    # Log.info("min_rows = 10")
    # Log.info("learn_rate = 0.1")
    # Log.info("==============================")
    # Log.info("==============================")
    # Log.info("scikit GBM Params: ")
    # Log.info("learning_rate=0.1")
    # Log.info("n_estimators=100")
    # Log.info("max_depth=5")
    # Log.info("min_samples_leaf = 10")
    # Log.info("n.minobsinnode = 10")
    # Log.info("max_features=None")
    # Log.info("==============================")

    ntrees = 100
    max_depth = 5
    min_rows = 10
    learn_rate = 0.1

    # Prepare data for scikit use
    trainData = np.genfromtxt(pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"),
                              delimiter=',',
                              dtype=None,
                              names=("Site","Angaus","SegSumT","SegTSeas","SegLowFlow","DSDist","DSMaxSlope","USAvgT",
                                     "USRainDays","USSlope","USNative","DSDam","Method","LocSed"),
                              skip_header=1,
                              missing_values=('NA'),
                              filling_values=(np.nan))
    trainDataResponse = trainData["Angaus"]
    trainDataFeatures = trainData[["SegSumT","SegTSeas","SegLowFlow","DSDist","DSMaxSlope","USAvgT",
                                   "USRainDays","USSlope","USNative","DSDam","Method","LocSed"]]


    ecology_train["Angaus"] = ecology_train["Angaus"].asfactor()
    # Train H2O GBM Model:
    gbm_h2o = h2o.gbm(x=ecology_train[2:], y=ecology_train["Angaus"], ntrees=ntrees, learn_rate=learn_rate,
                      max_depth=max_depth, min_rows=min_rows, distribution="bernoulli")

    # Train scikit GBM Model:
    gbm_sci = ensemble.GradientBoostingClassifier(learning_rate=learn_rate, n_estimators=ntrees, max_depth=max_depth,
                                                  min_samples_leaf=min_rows, max_features=None)
    gbm_sci.fit(trainDataFeatures[:,np.newaxis],trainDataResponse)

    # Evaluate the trained models on test data
    # Load the test data (h2o)
    ecology_test = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_eval.csv"))

    # Load the test data (scikit)
    testData = np.genfromtxt(pyunit_utils.locate("smalldata/gbm_test/ecology_eval.csv"),
                              delimiter=',',
                              dtype=None,
                              names=("Angaus","SegSumT","SegTSeas","SegLowFlow","DSDist","DSMaxSlope","USAvgT",
                                     "USRainDays","USSlope","USNative","DSDam","Method","LocSed"),
                              skip_header=1,
                              missing_values=('NA'),
                              filling_values=(np.nan))
    testDataResponse = testData["Angaus"]
    testDataFeatures = testData[["SegSumT","SegTSeas","SegLowFlow","DSDist","DSMaxSlope","USAvgT",
                                   "USRainDays","USSlope","USNative","DSDam","Method","LocSed"]]

    # Score on the test data and compare results

    # scikit
    auc_sci = roc_auc_score(testDataResponse, gbm_sci.predict_proba(testDataFeatures[:,np.newaxis])[:,1])

    # h2o
    gbm_perf = gbm_h2o.model_performance(ecology_test)
    auc_h2o = gbm_perf.auc()

    #Log.info(paste("scikit AUC:", auc_sci, "\tH2O AUC:", auc_h2o))
    assert auc_h2o >= auc_sci, "h2o (auc) performance degradation, with respect to scikit"




if __name__ == "__main__":
    pyunit_utils.standalone_test(ecologyGBM)
else:
    ecologyGBM()
