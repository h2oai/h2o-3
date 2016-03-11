from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.kmeans import H2OKMeansEstimator

def pyunit_model_params():
    pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    m = H2OKMeansEstimator(k=4)
    m.train(x=list(range(pros.ncol)),training_frame=pros)
    print(m.params)
    print(m.full_parameters)

if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_model_params)
else:
  pyunit_model_params()
