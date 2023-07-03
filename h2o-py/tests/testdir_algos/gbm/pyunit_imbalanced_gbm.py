from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def imbalanced_gbm():
  covtype = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
  covtype[54] = covtype[54].asfactor()

  hh_imbalanced = H2OGradientBoostingEstimator(ntrees=10,
                                               nfolds=3,
                                               distribution="multinomial",
                                               balance_classes=False, seed=123456789)
  hh_imbalanced.train(x=list(range(54)), y=54, training_frame=covtype)
  hh_imbalanced_perf = hh_imbalanced.model_performance(covtype)
  hh_imbalanced_perf.show()

  hh_balanced = H2OGradientBoostingEstimator(ntrees=10,
                                             nfolds=3,
                                             distribution="multinomial",
                                             balance_classes=True, seed=123456789)
  hh_balanced.train(x=list(range(54)), y=54, training_frame=covtype)
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

  assert class_6_err_imbalanced >= 0.90*class_6_err_balanced, "balance_classes makes it at least 10% worse: imbalanced %d, balanced %d" % (class_6_err_imbalanced, class_6_err_balanced)



if __name__ == "__main__":
  pyunit_utils.standalone_test(imbalanced_gbm)
else:
  imbalanced_gbm()
