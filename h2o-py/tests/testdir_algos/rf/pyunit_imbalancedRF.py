import sys
sys.path.insert(1, "../../../")
import h2o

def imbalanced(ip,port):
    
    

    covtype = h2o.import_file(path=h2o.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()

    imbalanced = h2o.random_forest(x=covtype[0:54], y=covtype[54], ntrees=10, balance_classes=False, nfolds=3)
    imbalanced_perf = imbalanced.model_performance(covtype)
    imbalanced_perf.show()

    balanced = h2o.random_forest(x=covtype[0:54], y=covtype[54], ntrees=10, balance_classes=True, seed=123, nfolds=3)
    balanced_perf = balanced.model_performance(covtype)
    balanced_perf.show()

    ##compare error for class 6 (difficult minority)
    class_6_err_imbalanced = imbalanced_perf.confusion_matrix().cell_values[5][7]
    class_6_err_balanced = balanced_perf.confusion_matrix().cell_values[5][7]

    print("--------------------")
    print("")
    print("class_6_err_imbalanced")
    print(class_6_err_imbalanced)
    print("")
    print("class_6_err_balanced")
    print(class_6_err_balanced)
    print("")
    print("--------------------")

    assert class_6_err_imbalanced >= 0.9*class_6_err_balanced, "balance_classes makes it at least 10% worse!"

if __name__ == "__main__":
  h2o.run_test(sys.argv, imbalanced)
