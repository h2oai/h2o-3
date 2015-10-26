import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def imbalance():
    

    print "Test checks if Deep Learning works fine with an imbalanced dataset"

    covtype = h2o.upload_file(pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()
    hh_imbalanced = h2o.deeplearning(x=covtype[0:54], y=covtype[54], l1=1e-5, activation="Rectifier", loss="CrossEntropy",
                                     hidden=[200,200], epochs=1, training_frame=covtype, balance_classes=False,
                                     reproducible=True, seed=1234)
    print hh_imbalanced

    hh_balanced = h2o.deeplearning(x=covtype[0:54], y=covtype[54], l1=1e-5, activation="Rectifier", loss="CrossEntropy",
                                   hidden=[200,200], epochs=1, training_frame=covtype, balance_classes=True,
                                   reproducible=True, seed=1234)
    print hh_balanced

    #compare overall logloss
    class_6_err_imbalanced = hh_imbalanced.logloss()
    class_6_err_balanced = hh_balanced.logloss()

    if class_6_err_imbalanced < class_6_err_balanced:
        print "--------------------"
        print ""
        print "FAIL, balanced error greater than imbalanced error"
        print ""
        print ""
        print "class_6_err_imbalanced"
        print class_6_err_imbalanced
        print ""
        print "class_6_err_balanced"
        print class_6_err_balanced
        print ""
        print "--------------------"

    assert class_6_err_imbalanced >= class_6_err_balanced, "balance_classes makes it worse!"



if __name__ == "__main__":
    pyunit_utils.standalone_test(imbalance)
else:
    imbalance()
