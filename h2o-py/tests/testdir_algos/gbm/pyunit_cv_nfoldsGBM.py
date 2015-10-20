import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def cv_nfoldsGBM():
  
  

  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate[1] = prostate[1].asfactor()
  prostate.summary()

  prostate_gbm = h2o.gbm(y=prostate[1], x=prostate[2:9], nfolds = 5, distribution="bernoulli")
  prostate_gbm.show()
  
  # Can specify both nfolds >= 2 and validation data at once
  try:
    h2o.gbm(y=prostate[1], x=prostate[2:9], nfolds=5, validation_y=prostate[1], validation_x=prostate[2:9], distribution="bernoulli")
    assert True
  except EnvironmentError:
    assert False, "expected an error"



if __name__ == "__main__":
    pyunit_utils.standalone_test(cv_nfoldsGBM)
else:
    cv_nfoldsGBM()
