setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

test.Glrm.mojo <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
      numTest = 1000 # set test dataset to contain 1000 rows
      params_prob_data <- setParmsData(numTest) # generate model parameters, random dataset
      modelAndDir<-buildModelSaveMojoGLRM(params_prob_data$params) # build the model and save mojo
      filename = filePath(modelAndDir$dirName, "in.csv", fsep=.Platform$file.sep)
      h2o.downloadCSV(params_prob_data$tDataset, filename)
      loadedModel = h2o.loadModel(filePath(modelAndDir$dirName, modelAndDir$model@model_id, fsep=.Platform$file.sep))
      lpredict = h2o.predict(loadedModel, params_prob_data$tDataset)
      twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, glrmReconstruct=TRUE) # perform H2O and mojo prediction and return frames
      ncols = ncol(twoFrames$h2oPredict)
      # I set enum columns to numeric before comparing them.
      for (ind in seq_len(ncols)) {
        temp1 = twoFrames$h2oPredict[ind]
        temp2 = twoFrames$mojoPredict[ind]
        if (h2o.isnumeric(temp1)) {
          compareFrames(temp1, temp2, prob=1, tolerance=1e-6)
        } else
          compareFrames(h2o.asnumeric(temp1), h2o.asnumeric(temp2), prob=1, tolerance=1e-6)
      }
      print("Comparing mojo predict and loaded model predict....")
      for (cind in seq_len(ncols)) {
        temp1 <- twoFrames$h2oPredict[cind]
        temp2 <- lpredict[cind]
        if (h2o.isnumeric(temp1))
          compareFrames(temp1, temp2, prob=1, tolerance=1e-6)
        else
          compareFrames(h2o.asnumeric(temp1), h2o.asnumeric(temp2), prob=1, tolerance=1e-6)
      }
      twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename)
      xFactorTest <- h2o.getFrame(paste("GLRMLoading", twoFrames$frameId, sep="_"))
      compareFrames(xFactorTest, twoFrames$mojoPredict, prob=1, tolerance =1e-6)
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
