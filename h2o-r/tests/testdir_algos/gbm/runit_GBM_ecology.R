setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.ecology <- function() {
  
  h2oTest.logInfo("==============================")
  h2oTest.logInfo("H2O GBM Params: ")
  h2oTest.logInfo("x = 3:13")
  h2oTest.logInfo("y = Angaus")
  h2oTest.logInfo("data = ecology.hex,")
  h2oTest.logInfo("n.trees = 100") 
  h2oTest.logInfo("interaction.depth = 5")
  h2oTest.logInfo("n.minobsinnode = 10") 
  h2oTest.logInfo("shrinkage = 0.1")
  h2oTest.logInfo("==============================")
  h2oTest.logInfo("==============================")
  h2oTest.logInfo("R GBM Params: ")
  h2oTest.logInfo("Formula: Angaus ~ ., data = ecology.data[,-1]")
  h2oTest.logInfo("distribution = gaussian")
  h2oTest.logInfo("ntrees = 100")
  h2oTest.logInfo("interaction.depth = 5")
  h2oTest.logInfo("n.minobsinnode = 10")
  h2oTest.logInfo("shrinkage = 0.1")
  h2oTest.logInfo("bag.fraction = 1")
  h2oTest.logInfo("==============================")
  n.trees <<- 100
  max_depth <<- 5
  min_rows <<- 10
  learn_rate <<- 1
  
  h2oTest.logInfo("Importing ecology_model.csv data...\n")
  print("=============================")
  print(h2oTest.locate("smalldata/gbm_test/ecology_model.csv"))
  print("=============================")
  ecology.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/ecology_model.csv"))
  ecology.sum <- summary(ecology.hex)
  h2oTest.logInfo("Summary of the ecology data from h2o: \n") 
  print(ecology.sum)
  
  #import csv data for R to use
  ecology.data <- read.csv(h2oTest.locate("smalldata/gbm_test/ecology_model.csv"), header = TRUE)
  ecology.data <- na.omit(ecology.data) #this omits NAs... does GBM do this? Perhaps better to model w/o doing this?
  
  h2oTest.logInfo("H2O GBM with parameters:\nntrees = 100, max_depth = 5, min_rows = 10, learn_rate = 0.1\n")
  #Train H2O GBM Model:
  ecology.h2o <- h2o.gbm(x = 3:13, 
                        y = "Angaus", 
           training_frame = ecology.hex,
                   ntrees = n.trees,
                max_depth = 5,
                 min_rows = 10,
               learn_rate = 0.1,
                     distribution = "gaussian")

  print(ecology.h2o)
  
  #Train R GBM Model: Using Gaussian distribution family for binary outcome OK... Also more comparable to H2O, which uses MSE
  ecology.r <- gbm(Angaus ~ ., data = ecology.data[,-1], distribution = "gaussian", 
                  n.trees = n.trees,
                  interaction.depth = 5, 
                  n.minobsinnode = 10, 
                  shrinkage = 0.1,
                  bag.fraction=1)

  ecologyTest.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/ecology_eval.csv"))
  ecologyTest.data <- read.csv(h2oTest.locate("smalldata/gbm_test/ecology_eval.csv")) 

  h2oTest.checkGBMModel(ecology.h2o, ecology.r, ecologyTest.hex, ecologyTest.data)
  
  
}

h2oTest.doTest("GBM: Ecology Data", test.GBM.ecology)

