import sys
sys.path.insert(1, "../../../")
import h2o

def imbalancedGBM(ip,port):
    # TODO: PUBDEV-1706
    # Connect to h2o
    h2o.init(ip,port)

    covtype = h2o.import_frame(path=h2o.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()

    hh_imbalanced = h2o.gbm(x=covtype[0:54], y=covtype[54], ntrees=50, balance_classes=False, nfolds=10, distribution="multinomial")
    hh_imbalanced_perf = hh_imbalanced.model_performance(covtype)
    hh_imbalanced_perf.show()

    hh_balanced = h2o.gbm(x=covtype[0:54], y=covtype[54], ntrees=50, balance_classes=True, nfolds=10, distribution="multinomial")
    hh_balanced_perf = hh_balanced.model_performance(covtype)
    hh_balanced_perf.show()

    #compare error for class 6 (difficult minority)
    #confusion_matrix element at position A,P for N classes is at: model$confusion[P*(N+1)-(N-A+1)]
    #Here, A=6 P=8, N=7 -> need element 8*(7+1)-(7-6+1) = 62

    class_6_err_imbalanced = hh_imbalanced_perf.error()[6]
    class_6_err_balanced = hh_balanced_perf.error()[6]

    if (class_6_err_imbalanced < class_6_err_balanced):
        print("--------------------")
        print("")
        print("FAIL, balanced error greater than imbalanced error")
        print("")
        print("")
        print("class_6_err_imbalanced")
        print(class_6_err_imbalanced)
        print("")
        print("class_6_err_balanced")
        print(class_6_err_balanced)
        print("")
        print("--------------------")

    assert class_6_err_imbalanced >= class_6_err_balanced, "balance_classes makes it worse!"

if __name__ == "__main__":
    h2o.run_test(sys.argv, imbalancedGBM)
