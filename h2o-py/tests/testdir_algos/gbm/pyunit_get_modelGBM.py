import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def get_modelGBM():
  
  

  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate.describe()
  prostate[1] = prostate[1].asfactor()
  prostate_gbm = h2o.gbm(y=prostate[1], x=prostate[2:9], distribution="bernoulli")
  prostate_gbm.show()

  prostate_gbm.predict(prostate)
  model = h2o.get_model(prostate_gbm._id)
  model.show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(get_modelGBM)
else:
    get_modelGBM()
