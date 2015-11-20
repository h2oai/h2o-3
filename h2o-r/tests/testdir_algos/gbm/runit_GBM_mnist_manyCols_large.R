


test.mnist.manyCols <- function() {
   fPath = tryCatch({
      locate("bigdata/laptop/mnist/train.csv.gz")
    }, warning= function(w) {
      print("File bigdata/laptop/mnist/train.csv.gz could not be found.  Please run ./gradlew syncBigdataLaptop (or gradlew.bat syncBigdataLaptop for Windows) to retrieve the file.")
    }, error= function(e) {
      print("File bigdata/laptop/mnist/train.csv.gz could not be found.  Please run ./gradlew syncBigdataLaptop (or gradlew.bat syncBigdataLaptop for Windows) to retrieve the file.")
    }, finally = {
      
    })

  Log.info("Importing mnist train data...\n")
  train.hex <- h2o.uploadFile(fPath, "train.hex")
  train.hex[,785] <- as.factor(train.hex[,785])
  Log.info("Check that tail works...")
  tail(train.hex)
  tail_ <- tail(train.hex)
  Log.info("Doing gbm on mnist training data.... \n")
  gbm.mnist <- h2o.gbm(x= 1:784, y = 785, training_frame = train.hex, ntrees = 1, max_depth = 1, min_rows = 10, learn_rate = 0.01, distribution = "multinomial")
  print(gbm.mnist)

  
}

doTest("Many Columns Test: MNIST", test.mnist.manyCols)

