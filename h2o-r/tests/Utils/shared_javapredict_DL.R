
heading("BEGIN TEST")
check.dl <- function(conn) {

  heading("Uploading train data to H2O")
  iris_train.hex <- h2o.importFile(conn, train)

  heading("Creating DL model in H2O")
  balance_classes <- if (exists("balance_classes")) balance_classes else FALSE

  if (autoencoder) {
      iris.dl.h2o <- h2o.deeplearning(x = x, training_frame = iris_train.hex, hidden = hidden, balance_classes = balance_classes, activation = activation, epochs = epochs, autoencoder = T)
  } else {
      iris.dl.h2o <- h2o.deeplearning(x = x, y = y, training_frame = iris_train.hex, hidden = hidden, balance_classes = balance_classes, activation = activation, epochs = epochs)
  }

  print(iris.dl.h2o)

  heading("Downloading Java prediction model code from H2O")
  model_key <- iris.dl.h2o@model_id
  tmpdir_name <- sprintf("%s/results/tmp_model_%s", TEST_ROOT_DIR, as.character(Sys.getpid()))
  cmd <- sprintf("rm -fr %s", tmpdir_name)
  safeSystem(cmd)
  cmd <- sprintf("mkdir -p %s", tmpdir_name)
  safeSystem(cmd)
  h2o.download_pojo(iris.dl.h2o, tmpdir_name)

  heading("Uploading test data to H2O")
  iris_test.hex <- h2o.importFile(conn, test)

  heading("Predicting in H2O")
  iris.dl.pred <- h2o.predict(iris.dl.h2o, iris_test.hex)
  summary(iris.dl.pred)
  head(iris.dl.pred)
  prediction1 <- as.data.frame(iris.dl.pred)
  cmd <- sprintf(   "%s/out_h2o.csv", tmpdir_name)
  write.csv(prediction1, cmd, quote=FALSE, row.names=FALSE)

  heading("Setting up for Java POJO")
  iris_test_with_response <- read.csv(test, header=T)
  iris_test_without_response <- iris_test_with_response[,x]
  write.csv(iris_test_without_response, file = sprintf("%s/in.csv", tmpdir_name), row.names=F, quote=F)
  cmd <- sprintf("curl http://%s:%s/3/h2o-genmodel.jar > %s/h2o-genmodel.jar", myIP, myPort, tmpdir_name)
  safeSystem(cmd)
  cmd <- sprintf("javac -cp %s/h2o-genmodel.jar -J-Xmx4g -J-XX:MaxPermSize=256m %s/%s.java", tmpdir_name, tmpdir_name, model_key)
  safeSystem(cmd)

  heading("Predicting with Java POJO")
  cmd <- sprintf("java -ea -cp %s/h2o-genmodel.jar:%s -Xmx4g -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m hex.genmodel.PredictCsv --header --model %s --input %s/in.csv --output %s/out_pojo.csv", tmpdir_name, tmpdir_name, model_key, tmpdir_name, tmpdir_name)
  safeSystem(cmd)

  heading("Comparing predictions between H2O and Java POJO")
  prediction2 <- read.csv(sprintf("%s/out_pojo.csv", tmpdir_name), header=T)
  if (nrow(prediction1) != nrow(prediction2)) {
    warning("Prediction mismatch")
    print(paste("Rows from H2O", nrow(prediction1)))
    print(paste("Rows from Java POJO", nrow(prediction2)))
    stop("Number of rows mismatch")
  }

  match <- all(norm(as.matrix(prediction1[,-1] - prediction2[,-1]), type="M") < 1e-4)
  if (! match) {
    for (i in 1:nrow(prediction1)) {
      rowmatches <- (norm(as.matrix(prediction1[i,-1] - prediction2[i,-1]), type="M") < 1e-4)
      if (! rowmatches) {
        print("----------------------------------------------------------------------")
        print("")
        print(paste("Prediction mismatch on data row", i, "of test file", test))
        print("")
        print(      "(Note: That is the 1-based data row number, not the file line number.")
        print(      "       If you have a header row, then the file line number is off by one.)")
        print("")
        print("----------------------------------------------------------------------")
        print("")
        print("Data from failing row")
        print("")
        print(iris_test_without_response[i,])
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
  }

  heading("Cleaning up tmp files")
  cmd <- sprintf("rm -fr %s", tmpdir_name)
  safeSystem(cmd)

  testEnd()
}

doTest("Deep Learning test", check.dl)
