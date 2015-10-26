import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def swpredsRF():
  # Training set has two predictor columns
  # X1: 10 categorical levels, 100 observations per level; X2: Unif(0,1) noise
  # Ratio of y = 1 per Level: cat01 = 1.0 (strong predictor), cat02 to cat10 = 0.5 (weak predictors)




  #Log.info("Importing swpreds_1000x3.csv data...\n")
  swpreds = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/swpreds_1000x3.csv"))
  swpreds["y"] = swpreds["y"].asfactor()

  #Log.info("Summary of swpreds_1000x3.csv from H2O:\n")
  #swpreds.summary()

  # Train H2O DRF without Noise Column
  from h2o.estimators.random_forest import H2ORandomForestEstimator

#Log.info("Distributed Random Forest with only Predictor Column")
  model1 = H2ORandomForestEstimator(ntrees=50, max_depth=20, nbins=500)
  model1.train(x="X1", y="y", training_frame=swpreds)
  model1.show()
  perf1 = model1.model_performance(swpreds)
  print(perf1.auc())

  # Train H2O DRF Model including Noise Column:
  #Log.info("Distributed Random Forest including Noise Column")
  model2 = H2ORandomForestEstimator(ntrees=50, max_depth=20, nbins=500)
  model2.train(x=["X1","X2"], y="y", training_frame=swpreds)
  model2.show()
  perf2 = model2.model_performance(swpreds)
  print(perf2.auc())



if __name__ == "__main__":
  pyunit_utils.standalone_test(swpredsRF)
else:
  swpredsRF()
