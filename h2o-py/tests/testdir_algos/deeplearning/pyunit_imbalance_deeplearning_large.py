import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils


def imbalance():
  print "Test checks if Deep Learning works fine with an imbalanced dataset"

  covtype = h2o.upload_file(pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
  covtype[54] = covtype[54].asfactor()

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator

  hh_imbalanced = H2ODeepLearningEstimator(l1=1e-5, activation="Rectifier",
                                           loss="CrossEntropy", hidden=[200,200], epochs=1,
                                           balance_classes=False,reproducible=True,
                                           seed=1234)
  hh_imbalanced.train(x=range(54),y=54, training_frame=covtype)
  print hh_imbalanced

  hh_balanced = H2ODeepLearningEstimator(l1=1e-5, activation="Rectifier",
                                         loss="CrossEntropy", hidden=[200,200], epochs=1,
                                         balance_classes=True,reproducible=True,
                                         seed=1234)
  hh_balanced.train(x=range(54),y=54,training_frame=covtype)
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
