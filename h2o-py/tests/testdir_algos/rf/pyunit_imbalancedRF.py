import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def imbalanced():



  covtype = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
  covtype[54] = covtype[54].asfactor()

  from h2o.estimators.random_forest import H2ORandomForestEstimator

  imbalanced = H2ORandomForestEstimator(ntrees=10, balance_classes=False, nfolds=3)
  imbalanced.train(x=range(54), y=54, training_frame=covtype)
  imbalanced_perf = imbalanced.model_performance(covtype)
  imbalanced_perf.show()

  balanced = H2ORandomForestEstimator(ntrees=10, balance_classes=True, seed=123, nfolds=3)
  balanced.train(x=range(54), y=54, training_frame=covtype)
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
  pyunit_utils.standalone_test(imbalanced)
else:
  imbalanced()
