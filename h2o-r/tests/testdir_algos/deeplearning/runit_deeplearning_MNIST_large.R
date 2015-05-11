setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.deeplearning_MNIST <- function(conn) {
  Log.info("Deep Learning MNIST Classification)")
  
  TRAIN <- "bigdata/laptop/mnist/train.csv.gz"
  TEST <- "bigdata/laptop/mnist/test.csv.gz"
  
  # set to FALSE for stand-alone demo
  if (TRUE) {
    train_hex <- h2o.uploadFile(conn, locate(TRAIN), destination_frame = "train")
    test_hex <- h2o.uploadFile(conn, locate(TEST))
  } else {
    library(h2o)
    conn <- h2o.init(nthreads=-1)
    homedir <- paste0(path.expand("~"),"/h2o-dev/") #modify if needed
    train_hex <- h2o.importFile(conn, path = paste0(homedir,TRAIN), header = F, sep = ',', destination_frame = 'train.hex')
    test_hex <- h2o.importFile(conn, path = paste0(homedir,TEST), header = F, sep = ',', destination_frame = 'test.hex')
  }
  
  # Turn response into a factor (we want classification)
  train_hex[,785] <- as.factor(train_hex[,785])
  test_hex[,785] <- as.factor(test_hex[,785])
  train_hex <- h2o.assign(train_hex, "train.hex")
  test_hex <- h2o.assign(test_hex, "test.hex")
  
  # 1) Train deep learning model
  dl_model <- h2o.deeplearning(x=c(1:784), y=785,
                               training_frame=train_hex,
                               activation="RectifierWithDropout",
                               input_dropout_ratio=0.2,
                               classification_stop=-1,  # Turn off early stopping
                               l1=1e-5,
                               hidden=c(256,256,512), epochs=1     # For RUnits
                               #hidden=c(1024,1024,2048), epochs=8000 # For world-record
                              )
  
  # 2) Compute test set error
  print(h2o.performance(dl_model, test_hex))
  print(h2o.performance(dl_model, train_hex))
  
  testEnd()
}

doTest("Deep Learning MNIST", check.deeplearning_MNIST)

