import sys
sys.path.insert(1, "../../")
import h2o,tests

def hexdev_394(ip,port):
  path = h2o.locate("smalldata/covtype/covtype.20k.data")
  trainraw = h2o.lazy_import(path)
  tsetup = h2o.parse_setup(trainraw)
  tsetup["column_types"][10] = "ENUM"
  tsetup["column_types"][11] = "ENUM"
  tsetup["column_types"][12] = "ENUM"
  train = h2o.parse_raw(tsetup)
  
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
                   distribution = "multinomial",
                   ntrees=100,
                   learn_rate=0.1,
                   max_depth=6) 

  print "KEEPING FRAME???"
  print train._keep

if __name__ == "__main__":
    tests.run_test(sys.argv, hexdev_394)
