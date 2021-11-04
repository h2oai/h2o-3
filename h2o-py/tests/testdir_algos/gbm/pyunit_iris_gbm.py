from __future__ import print_function

import os
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def iris_gbm():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  train.describe()

  # Run GBM

  my_gbm = H2OGradientBoostingEstimator(ntrees=50,
                                        learn_rate=0.1,
                                        distribution="multinomial")
  my_gbm.train(x=list(range(1,4)),y="class",training_frame=train,validation_frame=train)
  my_gbm.show()

  my_gbm_metrics = my_gbm.model_performance(train)
  my_gbm_metrics.show()

  print(my_gbm_metrics)  #.show(criterion=my_gbm_metrics.theCriteria.PRECISION)

  fn = "plot.png"
  my_gbm.plot(save_plot_path=fn)
  if os.path.isfile(fn):
    os.remove(fn)
  plot_result = my_gbm.plot()
  plot_result.figure.savefig(fn)
  if os.path.isfile(fn):
    os.remove(fn)

if __name__ == "__main__":
  pyunit_utils.standalone_test(iris_gbm)
else:
  iris_gbm()
