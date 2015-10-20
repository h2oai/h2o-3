import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pyunit_model_params():

  pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))

  m = h2o.kmeans(pros,k=4)
  print m.params
  print m.full_parameters



if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_model_params)
else:
  pyunit_model_params()
