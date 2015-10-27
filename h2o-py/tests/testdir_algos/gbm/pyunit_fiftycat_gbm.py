import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def fiftycat_gbm():
  # Training set has only 45 categories cat1 through cat45
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/50_cattest_train.csv"))
  train["y"] = train["y"].asfactor()

  # Train H2O GBM Model:
  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  model = H2OGradientBoostingEstimator(distribution="bernoulli",
                                       ntrees=10,
                                       max_depth=5,
                                       nbins=20)
  model.train(x=["x1","x2"],y="y", training_frame=train)
  model.show()

  # Test dataset has all 50 categories cat1 through cat50
  test = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/50_cattest_test.csv"))


  # Predict on test dataset with GBM model:
  predictions = model.predict(test)
  predictions.show()

  # Get the confusion matrix and AUC
  performance = model.model_performance(test)
  test_cm = performance.confusion_matrix()
  test_auc = performance.auc()



if __name__ == "__main__":
  pyunit_utils.standalone_test(fiftycat_gbm)
else:
  fiftycat_gbm()
