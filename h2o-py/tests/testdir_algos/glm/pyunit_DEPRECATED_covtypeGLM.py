import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import random

def covtype():

  
  

  # Log.info("Importing covtype.20k.data...\n")
  covtype = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
  #
  myY = 54
  myX = [x for x in range(0,54) if x not in [20,28]]

  # Set response to be indicator of a particular class
  res_class = random.randint(1,4)
  # Log.info(paste("Setting response column", myY, "to be indicator of class", res_class, "\n"))
  covtype[54] = (covtype[54] == res_class)

  #covtype.summary()

  # L2: alpha = 0, lambda = 0
  covtype_mod1 = h2o.glm(y=covtype[myY], x=covtype[myX], family="binomial", alpha=[0], Lambda=[0])
  covtype_mod1.show()

  # Elastic: alpha = 0.5, lambda = 1e-4
  covtype_mod2 = h2o.glm(y=covtype[myY], x=covtype[myX], family="binomial", alpha=[0.5], Lambda=[1e-4])
  covtype_mod2.show()

  # L1: alpha = 1, lambda = 1e-4
  covtype_mod3 = h2o.glm(y=covtype[myY], x=covtype[myX], family="binomial", alpha=[1], Lambda=[1e-4])
  covtype_mod3.show()




if __name__ == "__main__":
    pyunit_utils.standalone_test(covtype)
else:
    covtype()
