doJavapredictTest <- function(model,test_file,test_frame,params, separator=",", setInvNumNA=FALSE) {
  conn <- h2o.getConnection()
  myIP <- conn@ip
  myPort <- conn@port

  print(paste("Creating", model, "model in H2O"))
  if (model == "glm") {
    model <- do.call("h2o.glm",params)
    print(model)
  } else if (model == "gbm") {
    model <- do.call("h2o.gbm",params)
    print(model)
  } else if (model == "randomForest") {
    model <- do.call("h2o.randomForest",params)
    print(model)
  } else if (model == "deeplearning") {
    model <- do.call("h2o.deeplearning",params)
    print(model)
  } else {
    stop(paste("Unknown model type", model))
  }

  print("Downloading Java prediction model code from H2O")
  model_key <- model@model_id
  tmpdir_name <- sprintf("%s/tmp_model_%s", sandbox(), as.character(Sys.getpid()))
  if (.Platform$OS.type == "windows") {
    shell(sprintf("C:\\cygwin64\\bin\\rm.exe -fr %s", normalizePath(tmpdir_name)))
    shell(sprintf("C:\\cygwin64\\bin\\mkdir.exe -p %s", normalizePath(tmpdir_name)))
  } else {
    safeSystem(sprintf("rm -fr %s", tmpdir_name))
    safeSystem(sprintf("mkdir -p %s", tmpdir_name))
  }
  h2o.download_pojo(model, tmpdir_name)

  print("Predicting in H2O")
  pred <- h2o.predict(model, test_frame)
  summary(pred)
  head(pred)
  prediction1 <- as.data.frame(pred)
  cmd <- sprintf(   "%s/out_h2o.csv", tmpdir_name)
  write.csv(prediction1, cmd, quote=FALSE, row.names=FALSE)

  print("Setting up for Java POJO") 
  # for missing column names, H2O use C1, C2,...  R uses X, Y,...
  test_with_response <- h2o.importFile(test_file, header=T)
  names(test_with_response) = names(test_frame) # replace R column names with H2O column names
  test_without_response <- as.data.frame(test_with_response[,params$x])
  if(is.null(ncol(test_without_response))) {
    test_without_response <- as.data.frame(test_without_response)
    colnames(test_without_response) <- params$x
  }
  #write.csv(test_without_response, file = sprintf("%s/in.csv", tmpdir_name), row.names=F, quote=F)
  file = sprintf("%s/in.csv", tmpdir_name)
  write.table(test_without_response, file, sep=separator, row.names=FALSE, quote=FALSE)
  cmd <- sprintf("curl http://%s:%s/3/h2o-genmodel.jar > %s/h2o-genmodel.jar", myIP, myPort, tmpdir_name)
  if (.Platform$OS.type == "windows") shell(cmd)
  else safeSystem(cmd)
  cmd <- sprintf("javac -cp %s/h2o-genmodel.jar -J-Xmx4g -J-XX:MaxPermSize=256m %s/%s.java", tmpdir_name, tmpdir_name, model_key)
  safeSystem(cmd)

  print("Predicting with Java POJO")
  if (.Platform$OS.type == "windows") cmd <- sprintf("java -ea -cp %s/h2o-genmodel.jar;%s -Xmx4g -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --header --model %s --input %s/in.csv --output %s/out_pojo.csv --separator %s", tmpdir_name, tmpdir_name, model_key, tmpdir_name, tmpdir_name, separator)
  else cmd <- sprintf("java -ea -cp %s/h2o-genmodel.jar:%s -Xmx4g -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --header --pojo %s --input %s/in.csv --output %s/out_pojo.csv --separator %s", tmpdir_name, tmpdir_name, model_key, tmpdir_name, tmpdir_name, separator)
  if (setInvNumNA)
    cmd <- paste(cmd, " --setConvertInvalidNum")


  safeSystem(cmd)

  print("Comparing predictions between H2O and Java POJO")
  prediction2 <- read.csv(sprintf("%s/out_pojo.csv", tmpdir_name), header=T, stringsAsFactors=TRUE)
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
        print("")
        print("Differences for each column")
        print("")
        r1<- prediction1[i,]
        r2 <-prediction2[i,]
        for (col in 1:ncol(prediction1)) {
          colmatch <- all.equal(r1[,col], r2[,col], tolerance = tolerance, check.names = FALSE)
          if (class(colmatch) != "logical" || !colmatch) {
            if (class(colmatch) == "logical") colmatch <- "not equal"
            print(paste("Column", col, r1[,col], r2[,col], "are", colmatch))
          }
        }
        print("")
        print("----------------------------------------------------------------------")
        print("")
        stop("Prediction mismatch")
      }
    }

    stop("Paranoid; should not reach here")
  }

  print("Cleaning up tmp files")
  if (.Platform$OS.type == "windows") shell(sprintf("C:\\cygwin64\\bin\\rm.exe -fr %s", normalizePath(tmpdir_name)))
  else safeSystem(sprintf("rm -fr %s", tmpdir_name))
  h2o.removeAll()
}
