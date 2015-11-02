import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def iris_gbm():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  train.describe()

  # Run GBM
  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  my_gbm = H2OGradientBoostingEstimator(ntrees=50,
                                        learn_rate=0.1,
                                        distribution="multinomial")
  my_gbm.train(x=range(1,4),y="class",training_frame=train,validation_frame=train)
  my_gbm.show()

  my_gbm_metrics = my_gbm.model_performance(train)
  my_gbm_metrics.show()

  print my_gbm_metrics  #.show(criterion=my_gbm_metrics.theCriteria.PRECISION)



if __name__ == "__main__":
  pyunit_utils.standalone_test(iris_gbm)
else:
  iris_gbm()
