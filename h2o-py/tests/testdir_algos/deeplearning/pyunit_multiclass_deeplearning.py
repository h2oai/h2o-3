import os, sys
sys.path.insert(1, "../../../")
import h2o, tests


def deeplearning_multi():
  print("Test checks if Deep Learning works fine with a multiclass training and test dataset")

  prostate = h2o.import_file(tests.locate("smalldata/logreg/prostate.csv"))

  prostate[4] = prostate[4].asfactor()

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator

  hh = H2ODeepLearningEstimator(loss="CrossEntropy")
  hh.train(X=[0,1],y=4, training_frame=prostate, validation_frame=prostate)
  hh.show()

if __name__ == '__main__':
  tests.run_test(sys.argv, deeplearning_multi)
