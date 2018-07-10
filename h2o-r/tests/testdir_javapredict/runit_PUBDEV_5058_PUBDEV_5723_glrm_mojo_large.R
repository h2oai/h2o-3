setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

test.Glrm.mojo <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    e <- tryCatch({
      browser()
      numTest = 1000 # set test dataset to contain 1000 rows
      params_prob_data <- setParmsData(numTest) # generate model parameters, random dataset
      modelAndDir<-buildModelSaveMojoGLRM(params_prob_data$params) # build the model and save mojo
      filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
      h2o.downloadCSV(params_prob_data$tDataset, filename)
      loadedModel = h2o.loadModel(paste(modelAndDir$dirName, modelAndDir$model@model_id, sep='/'))
      lpredict = h2o.predict(loadedModel, params_prob_data$tDataset)
      twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, glrmReconstruct=TRUE) # perform H2O and mojo prediction and return frames
      compareFrames(twoFrames$h2oPredict,twoFrames$mojoPredict, prob=1, tolerance = 1e-6)
      print("Comparing mojo predict and loaded model predict....")
      compareFrames(twoFrames$h2oPredict, lpredict, prob=1, tolerance=1e-6)
      twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename)
      xFactorTest <- h2o.getFrame(paste("GLRMLoading", twoFrames$frameId, sep="_"))
      compareFrames(xFactorTest, twoFrames$mojoPredict, prob=1, tolerance =1e-6)
    }, error = function(x) x)
    if (!is.null(e)&& (!all(sapply("wget", grepl, e[[1]]))))
      FAIL(e)   # throw error unless it is the stupid wget error.
  }

setParmsData <- function(numTest=1000) {
  #----------------------------------------------------------------------
  # Parameters for the test.
  #----------------------------------------------------------------------
  missing_values <- 'MeanImputation'
  
  training_file <- random_dataset("regression", testrow = numTest)
  ratios <- (h2o.nrow(training_file)-numTest)/h2o.nrow(training_file)
  allFrames <- h2o.splitFrame(training_file, ratios)
  training_frame <- allFrames[[1]]
  test_frame <- allFrames[[2]]
  
  params                  <- list()
  params$training_frame <- training_frame
  params$max_iterations <- 10
  params$k <- 3

  return(list("params" = params, "tDataset" = test_frame))
}

doTest("GLRM mojo test", test.Glrm.mojo)
