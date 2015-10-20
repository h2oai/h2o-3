import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




######################################################
#
# Sample Running GBM on iris_wheader.csv

def irisGBM():
  # Connect to a pre-existing cluster
    # connect to localhost:54321

  # Import training data
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  train.describe()

  # Run GBM
  my_gbm = h2o.gbm(           y=train["class"],
                   validation_y=train["class"],
                              x=train[1:4],
                   validation_x=train[1:4],
                   ntrees=50,
                   learn_rate=0.1,
                   distribution="multinomial")
  my_gbm.show()

  my_gbm_metrics = my_gbm.model_performance(train)
  my_gbm_metrics.show()

  my_gbm_metrics  #.show(criterion=my_gbm_metrics.theCriteria.PRECISION)



if __name__ == "__main__":
    pyunit_utils.standalone_test(irisGBM)
else:
    irisGBM()
