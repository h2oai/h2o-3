setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

test.Glrm.mojo <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    numTest = 200 # set test dataset to contain 1000 rows
    params_prob_data <- setParmsData(numTest) # generate model parameters, random dataset
    modelAndDir<-buildModelSaveMojoGLRM(params_prob_data$params) # build the model and save mojo
    filename = filePath(modelAndDir$dirName, "in.csv", fsep=.Platform$file.sep)
    h2o.downloadCSV(params_prob_data$tDataset, filename)
    loadedModel = h2o.loadModel(filePath(modelAndDir$dirName, modelAndDir$model@model_id, fsep=.Platform$file.sep))
    lpredict = h2o.predict(loadedModel, params_prob_data$tDataset)
    twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, glrmIterNumber=100) # perform H2O and mojo prediction and return frames
    xFactor <- h2o.getFrame(paste("GLRMLoading", twoFrames$frameId, sep="_"))
      
    time2Iter<-system.time(twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, glrmIterNumber=2)) # perform H2O and mojo prediction and return frames
    time8000Iter <- system.time(twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, glrmIterNumber=8000)) # perform H2O and mojo prediction and return frames
    print("Time(s) taken for 2 iterations is ")
    print(time2Iter[3])
    print("Time(s) taken for 8000 iterations is ")
    print(time8000Iter[3])
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
