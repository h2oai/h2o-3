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

test.deeplearning.autoencoder.mojo <-
  function() {
    numTest = 200 # set test dataset to contain 1000 rows
    allAct <-
      c(
        "Tanh",
        "TanhWithDropout",
        "Rectifier",
        "RectifierWithDropout"
      )
    problemType <- c("binomial", "multinomial", "regression")
    missingValues <- c('MeanImputation', 'Skip')
    categoricalEncodings <-
      c("Eigen", "AUTO", "OneHotInternal", "Binary")
    model_count = 1
    useAllFactors <- TRUE
    toStandardize <- FALSE
    
    for (missing_values in missingValues) {
      for (cateEn in categoricalEncodings) {
        for (actFunc in allAct) {
          for (response_type in problemType) {
            if (grepl('regression', response_type, fixed = TRUE)) {
              response_num = 1
            } else if (grepl('binomial', response_type, fixed = TRUE)) {
              response_num = 2
            } else {
              response_num = 3
            }
            browser()
            print(paste("*******   Model number", model_count, sep =
                          ":"))
            model_count = model_count + 1
            print(paste("useAllFactor", useAllFactors, sep = ":"))
            print(paste("activation function", actFunc, sep = ":"))
            print(paste("toStandardsize", toStandardize, sep = ":"))
            print(paste("missing values handling", missing_values, sep =
                          ":"))
            print(paste("categorical encodings", cateEn, sep = ":"))
            print(paste("response type", response_type, sep = ":"))
            print("Generating model parameters and dataset....")
            params_prob_data <- setParmsData(
              useAllFactors,
              actFunc,
              toStandardize,
              missing_values,
              cateEn,
              response_type,
              response_num
            )
            e <- tryCatch({           
            print("Building model and saving mojo....")
            modelAndDir <-
              buildModelSaveMojo(params_prob_data$params) # build the model and save mojo
            filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
            h2o.downloadCSV(params_prob_data$tDataset[, params_prob_data$params$x], filename)
            print("Generating model predict and mojo predict .....")
            twoFrames <-
              mojoH2Opredict(modelAndDir$model,
                             modelAndDir$dirName,
                             filename) # perform H2O and mojo prediction and return frames
            print("Comparing model predict and mojo predict .....")
            compareFrames(
              twoFrames$h2oPredict,
              twoFrames$mojoPredict,
              prob = 0.1,
              tolerance = 1e-4
            )
            }, error = function(x) x)
            if (!is.null(e)) {
              print("Oh, caught some error")
              print(typeof(e))
              if (!is.null(e) && (!all(sapply("DistributedException", grepl, e[[1]]))))
                FAIL(e)   # throw error unless it is unstable model error. 
            }

            print("Test SUCCESS....")
          }
        }
      }
    }
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
                                        '/'), header=T)
  
  return(list("h2oPredict"=predictions1, "mojoPredict"=predictions2))
}

buildModelSaveMojo <- function(params) {
  model <- do.call("h2o.deeplearning", params)
  model_key <- model@model_id
  tmpdir_name <- sprintf("%s/tmp_model_%s", sandbox(), as.character(Sys.getpid()))
  h2o.saveMojo(model, path = tmpdir_name, force = TRUE) # save mojo
  h2o.saveModel(model, path = tmpdir_name, force=TRUE) # save model to compare mojo/h2o predict offline
  
  return(list("model"=model, "dirName"=tmpdir_name))
}

setParmsData <- function(useAllFactors, actFunc, toStandardize, missing_values, cateEn, training_frame, response_type, response_num) {
    if (grepl('regression', response_type, fixed = TRUE))  {
        response_num <- 1
    } else if (grepl('binomial', response_type, fixed = TRUE))  {
        response_num <- 2
    } else {
        response_num <- 3
    }
    training_file <- random_dataset_fixed_size(response_type, 8000, 5, response_num, testrow = 200)
    ratios <- (h2o.nrow(training_file)-200)/h2o.nrow(training_file)
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
    hidden <- c(2,3)
    if (grepl('Dropout', actFunc, fixed = TRUE)) {
        hiddenDropouts <- c(0.5, 0.5)
    } else {
        hiddenDropouts <- c()
    }
    nn_structure <- list("hidden" = hidden, "hiddenDropouts" = hiddenDropouts)
    params$hidden <- nn_structure$hidden
    params$training_frame <- training_frame
    params$x <- allNames[-which(allNames=="response")]
    params$autoencoder <- TRUE
    if (!params$autoencoder)
        params$y <- "response"
    if (length(nn_structure$hiddenDropouts) > 0) {
        params$input_dropout_ratio <- 0.5
        params$hidden_dropout_ratios <- nn_structure$hiddenDropouts
    }
    return(list("params" = params, "tDataset" = test_frame))
}


doTest("Deeplearning autoencoder mojo test", test.deeplearning.autoencoder.mojo)
