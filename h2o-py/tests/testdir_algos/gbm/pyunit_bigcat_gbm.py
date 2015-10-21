import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def bigcat_gbm():
  #Log.info("Importing bigcat_5000x2.csv data...\n")
  bigcat = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/bigcat_5000x2.csv"))
  bigcat["y"] = bigcat["y"].asfactor()
  #Log.info("Summary of bigcat_5000x2.csv from H2O:\n")
  #bigcat.summary()

  # Train H2O GBM Model:
  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  model = H2OGradientBoostingEstimator(distribution="bernoulli",
                                       ntrees=1,
                                       max_depth=1,
                                       nbins=100)
  model.train(x="X",y="y", training_frame=bigcat)
  model.show()
  performance = model.model_performance(bigcat)
  performance.show()

  # Check AUC and overall prediction error
  #test_accuracy = performance.accuracy()
  test_auc = performance.auc()

if __name__ == "__main__":
  pyunit_utils.standalone_test(bigcat_gbm)
else:
  bigcat_gbm()
