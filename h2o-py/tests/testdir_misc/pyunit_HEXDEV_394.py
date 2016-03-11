import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def hexdev_394():
  path = pyunit_utils.locate("smalldata/covtype/covtype.20k.data")
  c_types = [None] * 55
  c_types[10] = "enum"
  c_types[11] = "enum"
  c_types[12] = "enum"
  train = h2o.import_file(path, col_types=c_types)

  cols = train.col_names  # This returned space for first column name
  x_cols = [colname for colname in cols if colname != "C55"]

  splits = train.split_frame()
  newtrain = splits[0]
  newvalid = splits[1]
  newtrain[54] = newtrain[54].asfactor()
  newvalid[54] = newvalid[54].asfactor()


  my_gbm = H2OGradientBoostingEstimator(distribution="multinomial", ntrees=100, learn_rate=0.1, max_depth=6)
  my_gbm.train(x=x_cols,y=54,training_frame=newtrain, validation_frame=newvalid)

  split1, split2 = train.split_frame()
  split1[54] = split1[54].asfactor()
  split2[54] = split2[54].asfactor()

  my_gbm = H2OGradientBoostingEstimator(distribution="multinomial",
                                        ntrees=100,
                                        learn_rate=0.1,
                                        max_depth=6)
  my_gbm.train(x=x_cols,y=54,training_frame=split1,validation_frame=split2)


if __name__ == "__main__":
    pyunit_utils.standalone_test(hexdev_394)
else:
    hexdev_394()
