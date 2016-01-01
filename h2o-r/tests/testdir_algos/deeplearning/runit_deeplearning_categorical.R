setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_multi <- function() {
  h2oTest.logInfo("Test checks if Deep Learning works fine with a categorical dataset")
  
  print(h2oTest.locate("smalldata/logreg/prostate.csv"))
  prostate <- h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), "prostate")
  prostate[,2] <- as.factor(prostate[,2]) #CAPSULE -> Factor (response)
  prostate[,3] <- as.factor(prostate[,3]) #AGE -> Factor
  prostate[,4] <- as.factor(prostate[,4]) #RACE -> Factor
  prostate[,5] <- as.factor(prostate[,5]) #DPROS -> Factor
  prostate[,6] <- as.factor(prostate[,6]) #DCAPS -> Factor
  print(prostate)

  hh <- h2o.deeplearning(x=c(3,4,5,6,7,8,9),y=2,training_frame=prostate,hidden=c(20,20),use_all_factor_levels=F,loss="CrossEntropy")
  print(hh)

  
}

h2oTest.doTest("Deep Learning MultiClass Test", check.deeplearning_multi)

