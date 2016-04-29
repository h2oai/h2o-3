setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_imbalanced <- function() {
  Log.info("Test checks if Deep Learning weights and biases are accessible from R")
  
  census <- h2o.uploadFile(locate("smalldata/chicago/chicagoCensus.csv"))
  census[,1] <- as.factor(census[,1])
  dlmodel<-h2o.deeplearning(x=c(1:3),y=4,hidden=c(17,191),epochs=1,
                            training_frame=census,balance_classes=F,
                            reproducible=T, seed=1234, export_weights_and_biases=T)
  #print(dlmodel)

  weights1 <- h2o.weights(dlmodel,matrix_id=1)
  print(head(weights1))
  weights2 <- h2o.weights(dlmodel,matrix_id=2)
  weights3 <- h2o.weights(dlmodel,matrix_id=3)

  biases1 <- h2o.biases(dlmodel,vector_id=1)
  biases2 <- h2o.biases(dlmodel,vector_id=2)
  biases3 <- h2o.biases(dlmodel,vector_id=3)

  checkTrue(ncol(weights1) == 79, "wrong dimensionality!")
  checkTrue(nrow(weights1) == 17, "wrong dimensionality!")

  checkTrue(ncol(weights2) == 17, "wrong dimensionality!")
  checkTrue(nrow(weights2) == 191, "wrong dimensionality!")

  checkTrue(ncol(weights3) == 191, "wrong dimensionality!")
  checkTrue(nrow(weights3) == 1, "wrong dimensionality!")

  checkTrue(ncol(biases1) == 1, "wrong dimensionality!")
  checkTrue(nrow(biases1) == 17, "wrong dimensionality!")

  checkTrue(ncol(biases2) == 1, "wrong dimensionality!")
  checkTrue(nrow(biases2) == 191, "wrong dimensionality!")

  checkTrue(ncol(biases3) == 1, "wrong dimensionality!")
  checkTrue(nrow(biases3) == 1, "wrong dimensionality!")



    df <- as.h2o(iris)
    dl1 <- h2o.deeplearning(1:4,5,df,hidden=c(10,10),export_weights_and_biases = TRUE, seed=1234, reproducible=T)
    p1 <- h2o.predict(dl1, df)
    ll1 <- h2o.logloss(h2o.performance(dl1,df))
    print(ll1)

    ## get weights and biases
    w1 <- h2o.weights(dl1,1)
    w2 <- h2o.weights(dl1,2)
    w3 <- h2o.weights(dl1,3)
    b1 <- h2o.biases(dl1,1)
    b2 <- h2o.biases(dl1,2)
    b3 <- h2o.biases(dl1,3)

    ## make a model from given weights/biases
    dl2 <- h2o.deeplearning(1:4,5,df,hidden=c(10,10),initial_weights=c(w1,w2,w3),initial_biases=c(b1,b2,b3), epochs=0)
    p2 <- h2o.predict(dl2, df)
    ll2 <- h2o.logloss(h2o.performance(dl2,df))
    print(ll2)
    #h2o.download_pojo(dl2) ## fully functional pojo

    ## check consistency
    checkTrue(max(abs(p1[,2:4]-p2[,2:4])) < 1e-6)
    checkTrue(abs(ll2 - ll1) < 1e-6)

    ## make another model with partially set weights/biases
    dl3 <- h2o.deeplearning(1:4,5,training_frame=df,hidden=c(10,10),initial_weights=list(w1,NULL,w3),initial_biases=list(b1,b2,NULL), epochs=10, seed=1234, reproducible=T)
    ll3 <- h2o.logloss(h2o.performance(dl3,df))
    checkTrue(ll3 < ll1)

    ## make another model with partially set user-modified weights/biases
    dl4 <- h2o.deeplearning(1:4,5,training_frame=df,hidden=c(10,10),initial_weights=list(w1*1.1,w2*0.9,sqrt(w3)),initial_biases=list(b1,b2,NULL), epochs=10, seed=1234, reproducible=T)
    ll4 <- h2o.logloss(h2o.performance(dl4,df))
}

doTest("Deep Learning Weights/Biases Test", check.deeplearning_imbalanced)
