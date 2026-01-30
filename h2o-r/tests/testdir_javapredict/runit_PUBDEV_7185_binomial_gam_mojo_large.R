setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

test.binomial.gam.mojo <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    numTest = 1000 # set test dataset to contain 1000 rows
    params_prob_data <-
      setParmsData(numTest) # generate model parameters, random dataset
    modelAndDir <-
      buildModelSaveMojoGAM(params_prob_data$params) # build the model and save mojo
    filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
    h2o.downloadCSV(params_prob_data$tDataset[, params_prob_data$params$x], filename)
    twoFrames <-
      mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename) # perform H2O and mojo prediction and return frames
    h2o.downloadCSV(twoFrames$h2oPredict,
                    sprintf("%s/h2oPred.csv", modelAndDir$dirName))
    h2o.downloadCSV(twoFrames$mojoPredict,
                    sprintf("%s/mojoOut.csv", modelAndDir$dirName))
    compareFrames(
      twoFrames$h2oPredict,
      twoFrames$mojoPredict,
      prob = 1,
      tolerance = 1e-4
    )
  }

setParmsData <- function(numTest = 1000) {
  #----------------------------------------------------------------------
  # Parameters for the test.
  #----------------------------------------------------------------------
  missing_values <- 'MeanImputation'
  
  training_file <- random_dataset("binomial", testrow = numTest)
  train_numeric <-
    random_dataset_real_only(h2o.nrow(training_file), 1)
  training_file <-
    h2o.cbind(training_file, train_numeric)  # make sure we have at least one numeric column
  ratios <-
    (h2o.nrow(training_file) - numTest) / h2o.nrow(training_file)
  allFrames <- h2o.splitFrame(training_file, ratios)
  training_frame <- allFrames[[1]]
  test_frame <- allFrames[[2]]
  allNames = h2o.names(training_frame)
  gam_cols <- c(allNames[length(allNames)])
  
  params                  <- list()
  params$missing_values_handling <- missing_values
  params$training_frame <- training_frame
  params$x <- allNames[-which(allNames == "response")]
  params$y <- "response"
  params$family <- "binomial"
  params$gam_columns <- gam_cols
  
  return(list("params" = params, "tDataset" = test_frame))
}

doTest("test gam mojo with binomial family", test.binomial.gam.mojo)
