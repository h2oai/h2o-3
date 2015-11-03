import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def deeplearning_multi():
    print("Test checks if Deep Learning works fine with a categorical dataset")

    # print(locate("smalldata/logreg/protstate.csv"))
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate[1] = prostate[1].asfactor()  #CAPSULE -> CAPSULE
    prostate[2] = prostate[2].asfactor()  #AGE -> Factor
    prostate[3] = prostate[3].asfactor()  #RACE -> Factor
    prostate[4] = prostate[4].asfactor()  #DPROS -> Factor
    prostate[5] = prostate[5].asfactor()  #DCAPS -> Factor
    prostate = prostate.drop('ID')        #remove ID
    prostate.describe()


    hh = h2o.deeplearning(x                     = prostate.drop('CAPSULE'),
                          y                     = prostate['CAPSULE'],
                          loss                  = 'CrossEntropy',
                          hidden                = [10, 10],
                          use_all_factor_levels = False)
    hh.show()

if __name__ == "__main__":
  pyunit_utils.standalone_test(deeplearning_multi)
else:
  deeplearning_multi()