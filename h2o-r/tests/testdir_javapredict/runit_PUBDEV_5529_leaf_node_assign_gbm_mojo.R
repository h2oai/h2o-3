setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

test.gbm.leaf.assignment.mojo <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    e <- tryCatch({
      numTest = 1000 # set test dataset to contain 1000 rows
      params_prob_data <- setParmsData(numTest) # generate model parameters, random dataset
      modelAndDir<-buildModelSaveMojoTrees(params_prob_data$params, 'gbm') # build the model and save mojo
      filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
      h2o.downloadCSV(params_prob_data$tDataset[,params_prob_data$params$x], filename)
      twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, get_leaf_node_assignment=TRUE) # perform H2O and mojo prediction and return frames
      print("Finished mojo.  Going to compare two frames")
      print(twoFrames)
      compareStringFrames(twoFrames$h2oPredict,twoFrames$mojoPredict, prob=1)
    }, error = function(x) x)
    if (!is.null(e)&& (!all(sapply("wget", grepl, e[[1]]))))
      FAIL(e)   # throw error unless it is the stupid wget error.
  }

setParmsData <- function(numTest=1000) {
  #----------------------------------------------------------------------
  # Parameters for the test.
  #----------------------------------------------------------------------
  problem <- sample(c('binomial', 'multinomial', 'regression'))[1]
  training_file <- random_dataset(problem, testrow = numTest)
  ratios <- (h2o.nrow(training_file)-numTest)/h2o.nrow(training_file)
  allFrames <- h2o.splitFrame(training_file, ratios)
  training_frame <- allFrames[[1]]
  test_frame <- allFrames[[2]]
  allNames = h2o.names(training_frame)
  
  params                  <- list()
  params$training_frame <- training_frame
  params$x <- allNames[-which(allNames=="response")]
  params$y <- "response"
  params$ntrees <- 50
  params$max_depth<-4

  return(list("params" = params, "tDataset" = test_frame))
}

doTest("GBM leaf assignment mojo test", test.gbm.leaf.assignment.mojo)
