import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def vi_reg():



  data = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/BostonHousing.csv"))
  #data.summary()
  from h2o.estimators.random_forest import H2ORandomForestEstimator

  rf = H2ORandomForestEstimator(ntrees=100, max_depth=20, nbins=100, seed=0)
  rf.train(x=range(13), y=13, training_frame=data)

  ranking = [rf._model_json['output']['variable_importances'].cell_values[v][0] for v in range(data.ncol-1)]
  print(ranking)
  assert tuple([ranking[0],ranking[1]]) == tuple(["rm","lstat"]), "expected specific variable importance ranking"



if __name__ == "__main__":
  pyunit_utils.standalone_test(vi_reg)
else:
  vi_reg()
