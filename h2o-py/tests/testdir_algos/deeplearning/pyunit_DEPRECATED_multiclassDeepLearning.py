import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def deeplearning_multi():
    

    print("Test checks if Deep Learning works fine with a multiclass training and test dataset")

    prostate = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    prostate[4] = prostate[4].asfactor()

    hh = h2o.deeplearning(x             = prostate[0:2],
                          y             = prostate[4],
                          validation_x  = prostate[0:2],
                          validation_y  = prostate[4],
                          loss          = 'CrossEntropy')
    hh.show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_multi)
else:
    deeplearning_multi()
