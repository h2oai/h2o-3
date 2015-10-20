import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def framesliceGBM():
  
  

  #Log.info("Importing prostate data...\n")
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate = prostate[1:9]

  #Log.info("Running GBM on a sliced data frame...\n")
  model = h2o.gbm(x=prostate[1:8], y = prostate[0])



if __name__ == "__main__":
    pyunit_utils.standalone_test(framesliceGBM)
else:
    framesliceGBM()
