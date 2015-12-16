from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator



def colsamplepertree():



  covtype = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
  covtype[54] = covtype[54].asfactor()

  splits = covtype.split_frame(ratios=[0.8], seed=1234)
  train = splits[0]
  valid = splits[1]

  regular = H2OGradientBoostingEstimator(ntrees=50, seed=1234)
  regular.train(x=list(range(54)), y=54, training_frame=train)
  mm_regular = regular.model_performance(valid)
  mm_regular.show()

  colsample = H2OGradientBoostingEstimator(ntrees=50, seed=1234, col_sample_rate_per_tree=0.9)
  colsample.train(x=list(range(54)), y=54, training_frame=train)
  mm_colsample = colsample.model_performance(valid)
  mm_colsample.show()

  ##compare error
  err_regular   = mm_regular.confusion_matrix().cell_values[7][7]
  err_colsample = mm_colsample.confusion_matrix().cell_values[7][7]

  print("--------------------")
  print("")
  print("err_regular")
  print(err_regular)
  print("")
  print("err_colsample")
  print(err_colsample)
  print("")
  print("--------------------")

  assert err_regular >= 0.9*err_colsample, "col sample per tree makes it worse!"



if __name__ == "__main__":
  pyunit_utils.standalone_test(colsamplepertree)
else:
  colsamplepertree()
