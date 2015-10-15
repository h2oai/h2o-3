import sys, os
sys.path.insert(1,"../../../")
import h2o, tests
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

def deeplearning_multi():


  print("Test checks if Deep Learning works fine with a categorical dataset")

  # print(locate("smalldata/logreg/protstate.csv"))
  prostate = h2o.import_file(path=tests.locate("smalldata/logreg/prostate.csv"))
  prostate[1] = prostate[1].asfactor()  #CAPSULE -> CAPSULE
  prostate[2] = prostate[2].asfactor()  #AGE -> Factor
  prostate[3] = prostate[3].asfactor()  #RACE -> Factor
  prostate[4] = prostate[4].asfactor()  #DPROS -> Factor
  prostate[5] = prostate[5].asfactor()  #DCAPS -> Factor
  prostate = prostate.drop('ID')        #remove ID
  prostate.describe()

  hh = H2ODeepLearningEstimator(loss="CrossEntropy",
                                hidden=[10,10],
                                use_all_factor_levels=False)
  hh.train(X=list(set(prostate.names) - {"CAPSULE"}), y="CAPSULE", training_frame=prostate)
  hh.show()

if __name__ == '__main__':
  tests.run_test(sys.argv, deeplearning_multi)
