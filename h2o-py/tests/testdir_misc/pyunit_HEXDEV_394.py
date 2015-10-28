import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def hexdev_394():
  path = pyunit_utils.locate("smalldata/covtype/covtype.20k.data")
  c_types = [None] * 55
  c_types[10] = "enum"
  c_types[11] = "enum"
  c_types[12] = "enum"
  train = h2o.import_file(path, col_types=c_types)

  cols = train.col_names  # This returned space for first column name
  x_cols = [colname for colname in cols if colname != "C55"]
  x_cols


  splits = train.split_frame()
  newtrain = splits[0]
  newvalid = splits[1]
  newtrain_x = newtrain[x_cols]
  newtrain_y = newtrain[54].asfactor()
  newvalid_x = newvalid[x_cols]
  newvalid_y = newvalid[54].asfactor()


  my_gbm = h2o.gbm(y=newtrain_y,
                   validation_y=newvalid_y,
                   x=newtrain_x,
                   validation_x=newvalid_x,
                   distribution =  "multinomial",
                   ntrees=100,
                   learn_rate=0.1,
                   max_depth=6)

  split1, split2 = train.split_frame()

  newtrain_x = split1[x_cols]
  newtrain_y = split1[54].asfactor()
  newvalid_x = split2[x_cols]
  newvalid_y = split2[54].asfactor()

  my_gbm = h2o.gbm(y=newtrain_y,
                   validation_y=newvalid_y,
                   x=newtrain_x,
                   validation_x=newvalid_x,
                   distribution="multinomial",
                   ntrees=100,
                   learn_rate=0.1,
                   max_depth=6)



if __name__ == "__main__":
    pyunit_utils.standalone_test(hexdev_394)
else:
    hexdev_394()
