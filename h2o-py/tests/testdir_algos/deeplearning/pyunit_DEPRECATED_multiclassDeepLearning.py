import os, sys



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


deeplearning_multi()
