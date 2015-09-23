
doJavapredictTest <- function(model, training_frame, test_file, test_frame, ...) {
  print("Uploading train data to H2O")

  print(paste("Creating", model, "model in H2O"))
  if (model == "glm") {
    model <- h2o.glm(training_frame = training_frame, ...)
    print(model)
  } else if (model == "gbm") {
    model <- h2o.gbm(training_frame = training_frame, ...)
    print(model)
  } else if (model == "randomForest") {
    model <- h2o.randomForest(training_frame = training_frame, ...)
    print(model)
  } else {
    stop(paste("Unknown model type", model))
  }
  
  print("Downloading Java prediction model code from H2O")
  model_key <- model@model_id
  tmpdir_name <- sprintf("%s/results/tmp_model_%s", TEST_ROOT_DIR, as.character(Sys.getpid()))
  cmd <- sprintf("rm -fr %s", tmpdir_name)
  safeSystem(cmd)
  cmd <- sprintf("mkdir -p %s", tmpdir_name)
  safeSystem(cmd)
  h2o.download_pojo(model, tmpdir_name)

  print("Uploading test data to H2O")

  print("Predicting in H2O")
  pred <- h2o.predict(model, test_frame)
  summary(pred)
  head(pred)
  prediction1 <- as.data.frame(pred)
  cmd <- sprintf(   "%s/out_h2o.csv", tmpdir_name)
  write.csv(prediction1, cmd, quote=FALSE, row.names=FALSE)

  print("Setting up for Java POJO")
  test_with_response <- read.csv(test_file, header=T)
  test_without_response <- test_with_response[,x]
  if(is.null(ncol(test_without_response))) {
    test_without_response <- data.frame(test_without_response)
    colnames(test_without_response) <- x
  }
  write.csv(test_without_response, file = sprintf("%s/in.csv", tmpdir_name), row.names=F, quote=F)
  cmd <- sprintf("curl http://%s:%s/3/h2o-genmodel.jar > %s/h2o-genmodel.jar", myIP, myPort, tmpdir_name)
  safeSystem(cmd)
  cmd <- sprintf("javac -cp %s/h2o-genmodel.jar -J-Xmx4g -J-XX:MaxPermSize=256m %s/%s.java", tmpdir_name, tmpdir_name, model_key)
  safeSystem(cmd)

  print("Predicting with Java POJO")
  cmd <- sprintf("java -ea -cp %s/h2o-genmodel.jar:%s -Xmx4g -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --header --model %s --input %s/in.csv --output %s/out_pojo.csv", tmpdir_name, tmpdir_name, model_key, tmpdir_name, tmpdir_name)
  safeSystem(cmd)

  print("Comparing predictions between H2O and Java POJO")
  prediction2 <- read.csv(sprintf("%s/out_pojo.csv", tmpdir_name), header=T)
  if (nrow(prediction1) != nrow(prediction2)) {
    warning("Prediction mismatch")
    print(paste("Rows from H2O", nrow(prediction1)))
    print(paste("Rows from Java POJO", nrow(prediction2)))
    stop("Number of rows mismatch")
  }

  tolerance = 1e-8
  match <- all.equal(prediction1, prediction2, tolerance = tolerance, check.names = FALSE)
  if (class(match) != "logical") {
    match <- FALSE
  }
  
  if (! match) {
    for (i in 1:nrow(prediction1)) {
      rowmatch <- all.equal(prediction1[i,], prediction2[i,], tolerance = tolerance, check.names = FALSE)
      if (class(rowmatch) != "logical") {
        rowmatch <- FALSE
      }
      if (! rowmatch) {
        print("----------------------------------------------------------------------")
        print("")
        print(paste("Prediction mismatch on data row", i, "of test file", test_file))
        print("")
        print(      "(Note: That is the 1-based data row number, not the file line number.")
        print(      "       If you have a header row, then the file line number is off by one.)")
        print("")
        print("----------------------------------------------------------------------")
        print("")
        print("Data from failing row")
        print("")
        print(test_without_response[i,])
        print("")
        print("----------------------------------------------------------------------")
        print("")
        print("Prediction from H2O")
        print("")
        print(prediction1[i,])
        print("")
        print("----------------------------------------------------------------------")
        print("")
        print("Prediction from Java POJO")
        print("")
        print(prediction2[i,])
        print("")
        print("----------------------------------------------------------------------")
        print("")
        stop("Prediction mismatch")
      }
    }

    stop("Paranoid; should not reach here")
  }

  print("Cleaning up tmp files")
  cmd <- sprintf("rm -fr %s", tmpdir_name)
  safeSystem(cmd)
  h2o.removeAll()
}
