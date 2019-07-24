setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This Runit test aims to test the correctness of deeplearning
# mojo implementation.  A random neural network is generated and trained
# with a random dataset.  The prediction from h2o predict and mojo
# predict on a test dataset will be compared and they should equal.  Note
# that each time this test is run, it chooses the model parameters
# randomly.  We hope to test all different network settings here.
#----------------------------------------------------------------------

test.deeplearning.mojo <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    numTest = 200 # set test dataset to contain 1000 rows
    set.seed(12345)
    allAct <-
      c(
        "Tanh",
        "TanhWithDropout",
        "Rectifier",
        "RectifierWithDropout",
        "Maxout",
        "MaxoutWithDropout"
      )
    problemType <- c("regression", "binomial", "multinomial")
    missingValues <- c('MeanImputation', 'Skip')
    categoricalEncodings <-
      c("Eigen", "AUTO", "OneHotInternal", "Binary")
    model_count = 1
    useAllFactors <- c(TRUE, FALSE)
    autoEncoder <- c(FALSE)
    
    for (actFunc in allAct) {
      for (autoEn in autoEncoder) {
        if ((
          grepl('Maxout', autoEn, fixed = TRUE) ||
          grepl('MaxoutWithDropout', autoEn, fixed = TRUE)
        ) && autoEn)
          continue
        for (toStandardize in useAllFactors) {
          for (useFactor in useAllFactors) {
            for (missing_values in missingValues) {
              for (cateEn in categoricalEncodings) {
                for (response_type in problemType) {
                  if (grepl('regression', response_type, fixed = TRUE)) {
                    response_num = 1
                  } else if (grepl('binomial', response_type, fixed = TRUE)) {
                    response_num = 2
                  } else {
                    response_num = 3
                  }
                  print(paste("*******   Model number", model_count, sep =
                                ":"))
                  if (model_count == 10) {
                    browser()
                  }
                  model_count = model_count + 1
                  print(paste("AutoEncoder on", autoEn, sep = ":"))
                  print(paste("useAllFactor", useFactor, sep = ":"))
                  print(paste("activation function", actFunc, sep = ":"))
                  print(paste("toStandardsize", toStandardize, sep = ":"))
                  print(paste(
                    "missing values handling",
                    missing_values,
                    sep =
                      ":"
                  ))
                  print(paste("categorical encodings", cateEn, sep = ":"))
                  print(paste("response type", response_type, sep = ":"))
                  print(paste("response_number", response_num, sep = ":"))
                  print("Generating model parameters and dataset....")
                  params_prob_data <- setParmsData(
                    useFactor,
                    actFunc,
                    toStandardize,
                    missing_values,
                    cateEn,
                    autoEn,
                    response_type,
                    response_num
                  )
                  columnTypes <-
                    h2o.getTypes(params_prob_data$params$training_frame)
                  colTypes <- c()
                  for (index in c(2:length(columnTypes))) {
                    if ((grepl('real', columnTypes[index], fixed = TRUE)) ||
                        (grepl('int', columnTypes[index], fixed = TRUE))) {
                      columnTypes[index] <- "Numeric"
                      colTypes <- c(colTypes, "Numeric")
                    } else
                      colTypes <- c(colTypes, columnTypes[[index]])
                  }
                  
                  colNames <-
                    h2o.names(params_prob_data$params$training_frame)
                  e <- tryCatch({
                    modelAndDir <-
                      buildModelSaveMojo(params_prob_data$params) # build the model and save mojo
                    filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
                    #h2o.downloadCSV(params_prob_data$tDataset[, params_prob_data$params$x], filename)
                    h2o.downloadCSV(params_prob_data$tDataset, filename)
                    colTypes = list(by.col.name = colNames[2:length(colNames)], types =
                                      colTypes)
                    print("Test column names and types")
                    print(colTypes)
                    print("Generating model predict and mojo predict .....")
                    twoFrames <-
                      mojoH2Opredict(modelAndDir$model,
                                     modelAndDir$dirName,
                                     filename,
                                     colTypes) # perform H2O and mojo prediction and return frames
                    h2o.downloadCSV(
                      twoFrames$h2oPredict,
                      paste(
                        modelAndDir$dirName,
                        "h2oPredict.csv",
                        sep = "/"
                      )
                    )
                    h2o.downloadCSV(
                      params_prob_data$params$training_frame,
                      paste(
                        modelAndDir$dirName,
                        "trainingData.csv",
                        sep = "/"
                      )
                    )
                    print("Comparing model predict and mojo predict .....")
                    compareFrames(
                      twoFrames$h2oPredict,
                      twoFrames$mojoPredict,
                      prob = 0.1,
                      tolerance = 1e-4
                    )
                  }
                  , error = function(x)
                    x)
                  if (!is.null(e)) {
                    print("Oh, got some problems")
                    print(e)
                    if (!is.null(e) &&
                        (!all(sapply("unstable", grepl, e[[1]])))) {
                      stop(e)
                      FAIL(e)   # throw error unless it is unstable model error.
                    }
                  }
                  print("Test SUCCESS....")
                }
              }
            }
          }
        }
      }
    }
  }

mojoH2Opredict <-
  function(model, tmpdir_name, filename, columnTypes) {
    newTest <- h2o.importFile(filename, col.types = columnTypes)
    predictions1 <- h2o.predict(model, newTest)
    
    a = strsplit(tmpdir_name, '/')
    endIndex <- (which(a[[1]] == "h2o-r")) - 1
    genJar <-
      paste(a[[1]][1:endIndex], collapse = '/')
    
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
          "java -ea -cp %s/h2o-assemblies/genmodel/build/libs/genmodel.jar -Xmx4g -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --header --mojo %s/%s --input %s/in.csv --output %s/out_mojo.csv --decimal",
          genJar,
          tmpdir_name,
          paste(model@model_id, "zip", sep = '.'),
          tmpdir_name,
          tmpdir_name
        )
    }
    safeSystem(cmd)  # perform mojo prediction
    predictions2 = h2o.importFile(paste(tmpdir_name, "out_mojo.csv", sep =
                                          '/'), header = T)
    
    return(list("h2oPredict" = predictions1, "mojoPredict" = predictions2))
  }

buildModelSaveMojo <- function(params) {
  model <- do.call("h2o.deeplearning", params)
  model_key <- model@model_id
  tmpdir_name <-
    sprintf("%s/tmp_model_%s", sandbox(), as.character(Sys.getpid()))
  h2o.saveMojo(model, path = tmpdir_name, force = TRUE) # save mojo
  h2o.saveModel(model, path = tmpdir_name, force = TRUE) # save model to compare mojo/h2o predict offline
  
  return(list("model" = model, "dirName" = tmpdir_name))
}

setParmsData <-
  function(useAllFactors,
           actFunc,
           toStandardize,
           missing_values,
           cateEn,
           autoen,
           response_type,
           response_num) {
    training_file <-
      random_dataset_fixed_size(response_type,
                                5000,
                                4,
                                response_num,
                                testrow = 200,
                                seed = 12345)
    ratios <-
      (h2o.nrow(training_file) - 200) / h2o.nrow(training_file)
    allFrames <- h2o.splitFrame(training_file, ratios)
    training_frame <- allFrames[[1]]
    test_frame <- allFrames[[2]]
    allNames = h2o.names(training_frame)
    
    params                  <- list()
    params$use_all_factor_levels <- useAllFactors
    params$activation <- actFunc
    params$standardize <- toStandardize
    params$missing_values_handling <- missing_values
    params$categorical_encoding <- cateEn
    hidden <- c(2, 3)
    if (grepl('Dropout', actFunc, fixed = TRUE)) {
      hiddenDropouts <- c(0.5, 0.5)
    } else {
      hiddenDropouts <- c()
    }
    nn_structure <-
      list("hidden" = hidden, "hiddenDropouts" = hiddenDropouts)
    params$hidden <- nn_structure$hidden
    params$training_frame <- training_frame
    params$x <- allNames[-which(allNames == "response")]
    params$autoencoder <- autoen
    params$seed <- 12345
    params$reproducible <- TRUE
    if (!params$autoencoder)
      params$y <- "response"
    if (length(nn_structure$hiddenDropouts) > 0) {
      params$input_dropout_ratio <- 0.5
      params$hidden_dropout_ratios <- nn_structure$hiddenDropouts
    }
    return(list("params" = params, "tDataset" = test_frame))
  }

doTest("Deeplearning mojo test", test.deeplearning.mojo)
