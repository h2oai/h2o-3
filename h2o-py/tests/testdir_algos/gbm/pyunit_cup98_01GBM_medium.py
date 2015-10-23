import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def cupMediumGBM():

  train = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/usecases/cup98LRN_z.csv"))
  test = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/usecases/cup98VAL_z.csv"))

  train["TARGET_B"] = train["TARGET_B"].asfactor()

  # Train H2O GBM Model:
  train_cols = train.names
  for c in ['C1', "TARGET_D", "TARGET_B", "CONTROLN"]:
    train_cols.remove(c)

  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  model = H2OGradientBoostingEstimator(distribution="bernoulli", ntrees=5)
  model.train(x=train_cols,y="TARGET_B", training_frame=train)


if __name__ == "__main__":
  pyunit_utils.standalone_test(cupMediumGBM)
else:
  cupMediumGBM()
