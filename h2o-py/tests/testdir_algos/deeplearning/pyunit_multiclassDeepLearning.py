import os, sys

import h2o, tests

def deeplearning_multi():
    

    print("Test checks if Deep Learning works fine with a multiclass training and test dataset")

    prostate = h2o.import_file(tests.locate("smalldata/logreg/prostate.csv"))

    prostate[4] = prostate[4].asfactor()

    hh = h2o.deeplearning(x             = prostate[0:2],
                          y             = prostate[4],
                          validation_x  = prostate[0:2],
                          validation_y  = prostate[4],
                          loss          = 'CrossEntropy')
    hh.show()


pyunit_test = deeplearning_multi
