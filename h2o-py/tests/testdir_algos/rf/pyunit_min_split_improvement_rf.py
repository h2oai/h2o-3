from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator

def min_split_improvement():

  train  = h2o.upload_file(pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
  train["C55"] = train["C55"].asfactor()

  hh1 = H2ORandomForestEstimator(ntrees=20, min_split_improvement=0)
  hh2 = H2ORandomForestEstimator(ntrees=20, min_split_improvement=1e-1)

  hh1.train(x=list(range(55)), y="C55", training_frame=train)
  hh2.train(x=list(range(55)), y="C55", training_frame=train)

  assert hh1.logloss() < hh2.logloss(), "Expected logloss to get worse with large min_split_improvement"


if __name__ == "__main__":
  pyunit_utils.standalone_test(min_split_improvement)
else:
  min_split_improvement()
