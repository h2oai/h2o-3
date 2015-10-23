import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def prostate_gbm():
  # Connect to a pre-existing cluster
  # connect to localhost:54321

  df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  df.describe()

  # Remove ID from training frame
  train = df.drop("ID")

  # For VOL & GLEASON, a zero really means "missing"
  vol = train['VOL']
  vol[vol == 0] = None
  gle = train['GLEASON']
  gle[gle == 0] = None

  # Convert CAPSULE to a logical factor
  train['CAPSULE'] = train['CAPSULE'].asfactor()

  # See that the data is ready
  train.describe()

  # Run GBM
  my_gbm = H2OGradientBoostingEstimator(ntrees=50,
                                        learn_rate=0.1,
                                        distribution="bernoulli")
  my_gbm.train(x=range(1, train.ncol),
               y="CAPSULE",
               training_frame=train,
               validation_frame=train)
  my_gbm.show()

  my_gbm_metrics = my_gbm.model_performance(train)
  my_gbm_metrics.show()

  print my_gbm_metrics  #.show(criterion=my_gbm_metrics.theCriteria.PRECISION)



if __name__ == "__main__":
  pyunit_utils.standalone_test(prostate_gbm)
else:
  prostate_gbm()
