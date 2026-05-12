setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.ecology <- function() {
  
  Log.info("==============================")
  Log.info("H2O GBM Params: ")
  Log.info("x = 3:13")
  Log.info("y = Angaus")
  Log.info("data = ecology.hex,")
  Log.info("n.trees = 100") 
  Log.info("interaction.depth = 5")
  Log.info("n.minobsinnode = 10") 
  Log.info("shrinkage = 0.1")
  Log.info("==============================")
  Log.info("==============================")
  Log.info("R GBM Params: ")
  Log.info("Formula: Angaus ~ ., data = ecology.data[,-1]")
  Log.info("distribution = gaussian")
  Log.info("ntrees = 100")
  Log.info("interaction.depth = 5")
  Log.info("n.minobsinnode = 10")
  Log.info("shrinkage = 0.1")
  Log.info("bag.fraction = 1")
  Log.info("==============================")
  n.trees <<- 100
  max_depth <<- 5
  min_rows <<- 10
  learn_rate <<- 1
  
  Log.info("Importing ecology_model.csv data...\n")
  print("=============================")
  print(locate("smalldata/gbm_test/ecology_model.csv"))
  print("=============================")
  ecology.hex <- h2o.uploadFile(locate("smalldata/gbm_test/ecology_model.csv"))
  ecology.sum <- summary(ecology.hex)
  Log.info("Summary of the ecology data from h2o: \n") 
  print(ecology.sum)
  
  #import csv data for R to use
  ecology.data <- read.csv(locate("smalldata/gbm_test/ecology_model.csv"), header = TRUE, stringsAsFactors=TRUE)
  ecology.data <- na.omit(ecology.data) #this omits NAs... does GBM do this? Perhaps better to model w/o doing this?
  
  Log.info("H2O GBM with parameters:\nntrees = 100, max_depth = 5, min_rows = 10, learn_rate = 0.1\n")
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

  ecologyTest.hex <- h2o.uploadFile(locate("smalldata/gbm_test/ecology_eval.csv"))
  ecologyTest.data <- read.csv(locate("smalldata/gbm_test/ecology_eval.csv")) 

  checkGBMModel(ecology.h2o, ecology.r, ecologyTest.hex, ecologyTest.data)
  
  
}

doTest("GBM: Ecology Data", test.GBM.ecology)

