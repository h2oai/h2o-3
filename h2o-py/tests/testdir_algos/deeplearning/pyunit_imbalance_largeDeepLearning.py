import sys
sys.path.insert(1,"../../../")
import h2o, tests

def imbalance(ip, port):
    

    print "Test checks if Deep Learning works fine with an imbalanced dataset"

    covtype = h2o.upload_file(h2o.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()
    hh_imbalanced = h2o.deeplearning(x=covtype[0:54], y=covtype[54], l1=1e-5, activation="Rectifier", loss="CrossEntropy",
                                     hidden=[200,200], epochs=1, training_frame=covtype, balance_classes=False,
                                     reproducible=True, seed=1234)
    print hh_imbalanced

    hh_balanced = h2o.deeplearning(x=covtype[0:54], y=covtype[54], l1=1e-5, activation="Rectifier", loss="CrossEntropy",
                                   hidden=[200,200], epochs=1, training_frame=covtype, balance_classes=True,
                                   reproducible=True, seed=1234)
    print hh_balanced

    #compare error for class 6 (difficult minority)
    class_6_err_imbalanced = hh_imbalanced.confusion_matrix(covtype).cell_values[5][7]
    class_6_err_balanced = hh_balanced.confusion_matrix(covtype).cell_values[5][7]

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

if __name__ == '__main__':
    tests.run_test(sys.argv, imbalance)
