setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

test.PCA.mojo <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
      numTest = 200 # set test dataset to contain 1000 rows
      pca_method <- c("GramSVD", "Power", "Randomized", "GLRM")
      params_prob_data <- setParmsData(numTest, pca_method[1]) # generate model parameters, random dataset
      modelAndDir<-buildModelSaveMojoPCA(params_prob_data$params) # build the model and save mojo
      filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
      h2o.downloadCSV(params_prob_data$tDataset, filename)
      twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename) # perform H2O and mojo prediction and return frames
      h2o.downloadCSV(twoFrames$h2oPredict, sprintf("%s/h2oPred.csv", modelAndDir$dirName))
      h2o.downloadCSV(twoFrames$mojoPredict, sprintf("%s/mojoOut.csv", modelAndDir$dirName))
      compareFrames(twoFrames$h2oPredict,twoFrames$mojoPredict, prob=1, tolerance = 1e-4)
  }


setParmsData <- function(numTest, pca_method) {
  #----------------------------------------------------------------------
  # Parameters for the test.
  #----------------------------------------------------------------------
  training_file <- random_dataset("regression", testrow = numTest)
  ratios <- (h2o.nrow(training_file)-numTest)/h2o.nrow(training_file)
  allFrames <- h2o.splitFrame(training_file, ratios)
  training_frame <- allFrames[[1]]
  test_frame <- allFrames[[2]]
  allNames = h2o.names(training_frame)
  
  params                  <- list()
  params$impute_missing <- FALSE
  params$training_frame <- training_frame
  params$pca_method <- pca_method
  params$transform <- "STANDARDIZE"
  params$use_all_factor_levels <- TRUE
  params$k <- 3

  return(list("params" = params, "tDataset" = test_frame))
}

doTest("PCA mojo test", test.PCA.mojo)
