import sys
sys.path.insert(1, "../../../")
import h2o

def imbalancedGBM(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    covtype = h2o.import_frame(path=h2o.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()

    hh_imbalanced = h2o.gbm(x=covtype[0:54], y=covtype[54], ntrees=10, balance_classes=False, nfolds=3, distribution="multinomial")
    hh_imbalanced_perf = hh_imbalanced.model_performance(covtype)
    hh_imbalanced_perf.show()

    hh_balanced = h2o.gbm(x=covtype[0:54], y=covtype[54], ntrees=10, balance_classes=True, nfolds=3, distribution="multinomial")
    hh_balanced_perf = hh_balanced.model_performance(covtype)
    hh_balanced_perf.show()

    #compare error for class 6 (difficult minority)
    class_6_err_imbalanced = hh_imbalanced_perf.confusion_matrix().cell_values[5][7]
    class_6_err_balanced = hh_balanced_perf.confusion_matrix().cell_values[5][7]

    print("--------------------")
    print("")
    print("class_6_err_imbalanced")
    print(class_6_err_imbalanced)
    print("")
    print("class_6_err_balanced")
    print(class_6_err_balanced)
    print("")
    print("--------------------")

    assert class_6_err_imbalanced >= 0.90*class_6_err_balanced, "balance_classes makes it at least 10% worse!"

if __name__ == "__main__":
    h2o.run_test(sys.argv, imbalancedGBM)
