import sys
sys.path.insert(1, "../../../")
import h2o

def imbalanced(ip,port):
    
    

    covtype = h2o.import_frame(path=h2o.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()

    imbalanced = h2o.random_forest(x=covtype[0:54], y=covtype[54], ntrees=10, balance_classes=False, nfolds=3)
    imbalanced_perf = imbalanced.model_performance(covtype)
    imbalanced_perf.show()

    balanced = h2o.random_forest(x=covtype[0:54], y=covtype[54], ntrees=10, balance_classes=True, seed=123, nfolds=3)
    balanced_perf = balanced.model_performance(covtype)
    balanced_perf.show()

    #compare overall logloss
    class_6_err_imbalanced = imbalanced.logloss()
    class_6_err_balanced = balanced.logloss()

    print("--------------------")
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
  h2o.run_test(sys.argv, imbalanced)
