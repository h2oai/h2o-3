import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def fiftycatRF():



  # Training set has only 45 categories cat1 through cat45
  #Log.info("Importing 50_cattest_train.csv data...\n")
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/50_cattest_train.csv"))
  train["y"] = train["y"].asfactor()

  #Log.info("Summary of 50_cattest_train.csv from H2O:\n")
  #train.summary()
  from h2o.estimators.random_forest import H2ORandomForestEstimator

  # Train H2O DRF Model:
  #Log.info(paste("H2O DRF with parameters:\nclassification = TRUE, ntree = 50, depth = 20, nbins = 500\n", sep = ""))
  model = H2ORandomForestEstimator(ntrees=50, max_depth=20, nbins=500)
  model.train(x=["x1", "x2"], y="y", training_frame=train)

  # Test dataset has all 50 categories cat1 through cat50
  #Log.info("Importing 50_cattest_test.csv data...\n")
  test = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/50_cattest_test.csv"))

  #Log.info("Summary of 50_cattest_test.csv from H2O:\n")
  #test.summary()

  # Predict on test dataset with DRF model:
  #Log.info("Performing predictions on test dataset...\n")
  preds = model.predict(test)
  preds.head()

  # Get the confusion matrix and AUC
  #Log.info("Confusion matrix of predictions (max accuracy):\n")
  perf = model.model_performance(test)
  perf.show()
  cm = perf.confusion_matrix()
  print(cm)



if __name__ == "__main__":
  pyunit_utils.standalone_test(fiftycatRF)
else:
  fiftycatRF()
