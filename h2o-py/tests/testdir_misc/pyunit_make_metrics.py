from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator


def pyunit_make_metrics():
  fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  fr["CAPSULE"] = fr["CAPSULE"].asfactor()
  fr["RACE"] = fr["RACE"].asfactor()
  fr.describe()

  response = "AGE"
  predictors = range(1,fr.ncol)
  model = H2OGradientBoostingEstimator()
  model.train(x=predictors,y=response,training_frame=fr)

  p = model.model_performance(train=True)
#  p = make_metrics(predicted,actual,"gaussian")

if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_make_metrics)
else:
  pyunit_make_metrics()