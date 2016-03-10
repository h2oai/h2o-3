from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def download_pojo():
  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  print("iris:")
  iris.show()
  m = H2OGradientBoostingEstimator()
  m.train(x=list(range(4)), y=4, training_frame=iris)
  h2o.download_pojo(m)


if __name__ == "__main__":
  pyunit_utils.standalone_test(download_pojo)
else:
  download_pojo()
