import sys
sys.path.insert(1,"../../../")
import h2o, tests


def imbalance():
  print "Test checks if Deep Learning works fine with an imbalanced dataset"

  covtype = h2o.upload_file(tests.locate("smalldata/covtype/covtype.20k.data"))
  covtype[54] = covtype[54].asfactor()

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator

  hh_imbalanced = H2ODeepLearningEstimator(l1=1e-5, activation="Rectifier",
                                           loss="CrossEntropy", hidden=[200,200], epochs=1,
                                           balance_classes=False,reproducible=True,
                                           seed=1234)
  hh_imbalanced.train(X=range(54),y=54, training_frame=covtype)
  print hh_imbalanced

  hh_balanced = H2ODeepLearningEstimator(l1=1e-5, activation="Rectifier",
                                         loss="CrossEntropy", hidden=[200,200], epochs=1,
                                         balance_classes=True,reproducible=True,
                                         seed=1234)
  hh_balanced.train(X=range(54),y=54,training_frame=covtype)
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

if __name__ == '__main__':
  tests.run_test(sys.argv, imbalance)
