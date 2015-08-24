import os, sys
sys.path.insert(1, "../../../")
import h2o

def deeplearning_multi(ip, port):
    

    print("Test checks if Deep Learning works fine with a multiclass training and test dataset")

    prostate = h2o.import_file(h2o.locate("smalldata/logreg/prostate.csv"))

    prostate[4] = prostate[4].asfactor()

    hh = h2o.deeplearning(x             = prostate[0:2],
                          y             = prostate[4],
                          validation_x  = prostate[0:2],
                          validation_y  = prostate[4],
                          loss          = 'CrossEntropy')
    hh.show()

if __name__ == '__main__':
    h2o.run_test(sys.argv, deeplearning_multi)
