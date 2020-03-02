setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../../scripts/h2o-r-test-setup.R")

test.fractionalbinomial <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------

      browser()
      params_prob_data <- setParmsData() # generate model parameters, random dataset
      modelAndDir<-buildModelSaveMojoGLM(params_prob_data$params) # build the model and save mojo
      filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
      h2o.downloadCSV(params_prob_data$tDataset[,params_prob_data$params$x], filename)
      twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename) # perform H2O and mojo prediction and return frames
      h2o.downloadCSV(twoFrames$h2oPredict, sprintf("%s/h2oPred.csv", modelAndDir$dirName))
      h2o.downloadCSV(twoFrames$mojoPredict, sprintf("%s/mojoOut.csv", modelAndDir$dirname))
      compareFrames(twoFrames$h2oPredict,twoFrames$mojoPredict, prob=1, tolerance = 1e-4)
  }

setParmsData <- function() {
  #----------------------------------------------------------------------
  # Parameters for the test.
  #----------------------------------------------------------------------
  missing_values <- 'MeanImputation'

  training_frame <- h2o.importFile(locate("smalldata/glm_test/fraction_binommialOrig.csv"))
  test_frame <- h2o.importFile(locate("smalldata/glm_test/fraction_binommialOrig.csv"))
  allNames = h2o.names(training_frame)
  
  params                  <- list()
  params$missing_values_handling <- missing_values
  params$training_frame <- training_frame
  params$x <- c("log10conc")
  params$y <- "y"
  params$family <- "fractionalbinomial"

  return(list("params" = params, "tDataset" = test_frame))
}

doTest("Fractoinal Binomial GLM pojo, mojo test", test.fractionalbinomial)
