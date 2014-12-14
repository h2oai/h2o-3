setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.deeplearning_anomaly <- function(conn) {
  Log.info("Deep Learning Anomaly Detection MNIST)")
  
  TRAIN <- "smalldata/mnist/train.csv.gz"
  TEST <- "smalldata/mnist/test.csv.gz"
  
  # set to FALSE for stand-alone demo
  if (TRUE) {
    train_hex <- h2o.uploadFile(conn, locate(TRAIN), key = "train")
    test_hex <- h2o.uploadFile(conn, locate(TEST))
    print(train_hex)
  } else {
    library(h2o)
    conn <- h2o.init()
    homedir <- paste0(path.expand("~"),"/h2o/") #modify if needed
    train_hex <- h2o.importFile(conn, path = paste0(homedir,TRAIN), header = F, sep = ',', key = 'train.hex')
    test_hex <- h2o.importFile(conn, path = paste0(homedir,TEST), header = F, sep = ',', key = 'test.hex')
  }
  
  predictors = c(1:784)
  resp = 785
  
  # unsupervised -> drop the response column (digit: 0-9)
  train_hex <- train_hex[,-resp]
  test_hex <- test_hex[,-resp]
  print(train_hex)
  
  # helper functions for display of handwritten digits
  # adapted from http://www.r-bloggers.com/the-essence-of-a-handwritten-digit/
  plotDigit <- function(mydata, rec_error) {
    len <- nrow(mydata)
    N <- ceiling(sqrt(len))
    par(mfrow=c(N,N),pty='s',mar=c(1,1,1,1),xaxt='n',yaxt='n')
    for (i in 1:nrow(mydata)) {
      colors<-c('white','black')
      cus_col<-colorRampPalette(colors=colors)
      z<-array(mydata[i,],dim=c(28,28))
      z<-z[,28:1]
      image(1:28,1:28,z,main=paste0("rec_error: ", round(rec_error[i],4)),col=cus_col(256))
    }
  }
  plotDigits <- function(data, rec_error, rows) {
    row_idx <- order(rec_error[,1],decreasing=F)[rows]
    my_rec_error <- rec_error[row_idx,]
    my_data <- as.matrix(as.data.frame(data[row_idx,]))
    plotDigit(my_data, my_rec_error)
  }
  
  
  ## START ANOMALY DETECTION DEMO
  
  # 1) LEARN WHAT'S NORMAL
  # train unsupervised Deep Learning autoencoder model on train_hex
  ae_model <- h2o.deeplearning(x=predictors,
                               y=42, #response is ignored (pick any non-constant predictor column index)
                               training_frame=train_hex,
                               activation="Tanh",
                               autoencoder=T,
                               hidden=c(50),
                               l1=1e-5,
                               ignore_const_cols=F,
                               epochs=1)
  
  
  # 2) DETECT OUTLIERS
  # anomaly app computes the per-row reconstruction error for the test data set
  # (passing it through the autoencoder model and computing mean square error (MSE) for each row)
  test_rec_error <- as.data.frame(h2o.anomaly(test_hex, ae_model))
  
  
  # 3) VISUALIZE OUTLIERS
  # Let's look at the test set points with low/median/high reconstruction errors.
  # We will now visualize the original test set points and their reconstructions obtained 
  # by propagating them through the narrow neural net.
  
  # Convert the test data into its autoencoded representation (pass through narrow neural net)
  test_recon <- h2o.predict(ae_model, test_hex)

  # The good
  # Let's plot the 25 digits with lowest reconstruction error.
  # First we plot the reconstruction, then the original scanned images.  
  plotDigits(test_recon, test_rec_error, c(1:25))
  plotDigits(test_hex,   test_rec_error, c(1:25))
  
  # The bad
  # Now the same for the 25 digits with median reconstruction error.
  plotDigits(test_recon, test_rec_error, c(4988:5012))
  plotDigits(test_hex,   test_rec_error, c(4988:5012))
  
  # The ugly
  # And here are the biggest outliers - The 25 digits with highest reconstruction error!
  plotDigits(test_recon, test_rec_error, c(9976:10000))
  plotDigits(test_hex,   test_rec_error, c(9976:10000))
  
  testEnd()
}

doTest("Deep Learning Anomaly Detection MNIST", check.deeplearning_anomaly)

