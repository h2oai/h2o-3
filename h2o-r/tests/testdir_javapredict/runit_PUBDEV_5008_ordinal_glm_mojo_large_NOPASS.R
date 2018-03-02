setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

test.ordinalGlm.mojo <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    e <- tryCatch({
      numTest = 1000 # set test dataset to contain 1000 rows
      params_prob_data <- setParmsData(numTest) # generate model parameters, random dataset
      modelAndDir<-buildModelSaveMojo(params_prob_data$params) # build the model and save mojo
      filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
      h2o.downloadCSV(params_prob_data$tDataset[,params_prob_data$params$x], filename)
      twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename) # perform H2O and mojo prediction and return frames
      h2o.downloadCSV(twoFrames$h2oPredict, sprintf("%s/h2oPred.csv", modelAndDir$dirName))
      h2o.downloadCSV(twoFrames$mojoPredict, sprintf("%s/mojoOut.csv", modelAndDir$dirname))
      compareFrames(twoFrames$h2oPredict,twoFrames$mojoPredict, prob=1, tolerance = 1e-4)
    }, error = function(x) x)
    if (!is.null(e)&& (!all(sapply("wget", grepl, e[[1]]))))
      FAIL(e)   # throw error unless it is the stupid wget error.
  }

mojoH2Opredict<-function(model, tmpdir_name, filename) {
  newTest <- h2o.importFile(filename)
  predictions1 <- h2o.predict(model, newTest)
  
  a = strsplit(tmpdir_name, '/')
  endIndex <-(which(a[[1]]=="h2o-r"))-1
  genJar <-
    paste(a[[1]][1:endIndex], collapse='/')
  
  if (.Platform$OS.type == "windows") {
    cmd <-
      sprintf(
        "java -ea -cp %s/h2o-assemblies/genmodel/build/libs/genmodel.jar -Xmx4g -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --header --mojo %s/%s --input %s/in.csv --output %s/out_mojo.csv",
        genJar,
        tmpdir_name,
        paste(model_key, "zip", sep = '.'),
        tmpdir_name,
        tmpdir_name
      )
  } else {
    cmd <-
      sprintf(
        "java -ea -cp %s/h2o-assemblies/genmodel/build/libs/genmodel.jar -Xmx12g -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --mojo %s/%s --input %s/in.csv --output %s/out_mojo.csv --decimal",
        genJar,
        tmpdir_name,
        paste(model@model_id, "zip", sep = '.'),
        tmpdir_name,
        tmpdir_name
      )
  }
  safeSystem(cmd)  # perform mojo prediction
  predictions2 = h2o.importFile(paste(tmpdir_name, "out_mojo.csv", sep =
                                        '/'), header=T)
  
  return(list("h2oPredict"=predictions1, "mojoPredict"=predictions2))
}

buildModelSaveMojo <- function(params) {
  model <- do.call("h2o.glm", params)
  model_key <- model@model_id
  tmpdir_name <- sprintf("%s/tmp_model_%s", sandbox(), as.character(Sys.getpid()))
  if (.Platform$OS.type == "windows") {
    shell(sprintf("C:\\cygwin64\\bin\\rm.exe -fr %s", normalizePath(tmpdir_name)))
    shell(sprintf("C:\\cygwin64\\bin\\mkdir.exe -p %s", normalizePath(tmpdir_name)))
  } else {
    safeSystem(sprintf("rm -fr %s", tmpdir_name))
    safeSystem(sprintf("mkdir -p %s", tmpdir_name))
  }
  h2o.saveMojo(model, path = tmpdir_name, force = TRUE) # save mojo
  h2o.saveModel(model, path = tmpdir_name, force=TRUE) # save model to compare mojo/h2o predict offline
  
  return(list("model"=model, "dirName"=tmpdir_name))
}

setParmsData <- function(numTest=1000) {
  #----------------------------------------------------------------------
  # Parameters for the test.
  #----------------------------------------------------------------------
  missing_values <- 'MeanImputation'
  
  training_file <- random_dataset("multinomial", testrow = numTest)
  ratios <- (h2o.nrow(training_file)-numTest)/h2o.nrow(training_file)
  allFrames <- h2o.splitFrame(training_file, ratios)
  training_frame <- allFrames[[1]]
  test_frame <- allFrames[[2]]
  allNames = h2o.names(training_frame)
  
  params                  <- list()
  params$missing_values_handling <- missing_values
  params$training_frame <- training_frame
  params$x <- allNames[-which(allNames=="response")]
  params$y <- "response"
  params$family <- "ordinal"

  return(list("params" = params, "tDataset" = test_frame))
}

random_dataset <-
  function(response_type,
           max_row = 25000,
           min_row = 15000,
           max_col = 10,
           min_col = 5,
           testrow = 1000) {
    num_rows <- round(runif(1, min_row, max_row))
    num_cols <- round(runif(1, min_col, max_col))
    if (response_type == 'regression') {
      response_num = 1
    } else if (response_type == 'binomial') {
      response_num = 2
    } else {
      # assume all else as multinomial
      response_num = round(runif(1, 3, 10))
    }
    
    # generate all the fractions
    fractions <-
      c(runif(1, 0, 1),
        runif(1, 0, 1),
        runif(1, 0, 1),
        runif(1, 0, 1),
        runif(1, 0, 1))
    fractions <- fractions / sum(fractions)
    random_frame <-
      h2o.createFrame(
        rows = num_rows,
        cols = num_cols,
        randomize = TRUE,
        has_response = TRUE,
        categorical_fraction = fractions[1],
        integer_fraction = fractions[2],
        binary_fraction = fractions[3],
        time_fraction = fractions[4],
        string_fraction = 0,
        response_factors = response_num,
        missing_fraction = runif(1, 0, 0.05)
      )
    
    return(random_frame)
  }

doTest("Ordinal GLM mojo test", test.ordinalGlm.mojo)
