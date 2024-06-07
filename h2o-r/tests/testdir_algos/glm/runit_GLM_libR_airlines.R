setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


require(LiblineaR)
require(ROCR)

test.LiblineaR.airlines <- function() {
  L1logistic <- function(train,trainLabels,test,testLabels,trainhex,testhex) {
    Log.info("Using these parameters for LiblineaR: \n")
    Log.info("   type =    0: Logistic Regression L1-Regularized\n")
    Log.info("   cost = 100: Cost of constraints parameter\n")
    Log.info("epsilon = 1E-4: Tolerance of termination criterion\n")
    Log.info("  cross =    0: No kfold cross-validation\n")
    
    dimnames(test) <- dimnames(train)
    
    LibR.m        <- LiblineaR(train, trainLabels,type=0, epsilon=1E-4, cost=100)
    LibRpreds     <- predict(LibR.m, test, proba=1, decisionValues=TRUE)
    LibRCM        <- table(testLabels, LibRpreds$predictions)
    LibRPrecision <- LibRCM[1] / (LibRCM[1] + LibRCM[3])
    LibRRecall    <- LibRCM[1] / (LibRCM[1] + LibRCM[2])
    LibRF1        <- 2 * (LibRPrecision * LibRRecall) / (LibRPrecision + LibRRecall)
    LibRAUC       <- performance(prediction(as.numeric(LibRpreds$predictions), testLabels), measure = "auc")@y.values
    
    Log.info("Using these parameters for H2O: \n")
    Log.info(" family =          'binomial': Logistic Regression\n")
    Log.info(" lambda = 1/ (cost * params) [3.8e-05]: Shrinkage Parameter\n")
    Log.info("  alpha =                           0.0: Elastic Net Parameter\n")
    Log.info("beta_epsilon=                     1E-04: Tolerance of termination criterion\n")
    Log.info(" nfolds =                             1: No kfold cross-validation\n")
    h2o.m <- h2o.glm(x            = c("DepTime", "ArrTime", "Distance"),
                                    #c("fYear","fMonth","fDayofMonth","fDayOfWeek","DepTime","ArrTime","UniqueCarrier","Origin","Dest","Distance"), 
                     y            = "IsDepDelayed_REC", 
                     training_frame         = trainhex, 
                     family       = "binomial",
                     nfolds      = 1, 
                     lambda       = 1 / (3*100),
                     alpha        = 0.0,
                     standardize  = TRUE,
                     beta_epsilon = 1E-4)
    
    h2op         <- predict(h2o.m, testhex)
    h2operf      <- h2o.performance(h2o.m, testhex)
    h2opreds     <- as.numeric(as.character((as.data.frame(h2op)[,1])))
    h2oCM        <- table(testLabels, h2opreds)
    h2oPrecision <- h2oCM[1] / (h2oCM[1] + h2oCM[3])
    h2oRecall    <- h2oCM[1] / (h2oCM[1] + h2oCM[2])
    h2oF1        <- 2 * (h2oPrecision * h2oRecall) / (h2oPrecision + h2oRecall)
    h2oAUC       <- performance(prediction(h2opreds, testLabels), measure = "auc")@y.values
    
    Log.info("                ============= H2O Performance =============\n")
    Log.info(paste("H2O AUC (performance(prediction(predictions,actual))): ", h2oAUC[[1]], "\n", sep = ""))
    Log.info(paste("                        H2O Precision (tp / (tp + fp): ", h2oPrecision, "\n", sep = ""))
    Log.info(paste("                           H2O Recall (tp / (tp + fn): ", h2oRecall, "\n", sep = ""))
    Log.info(paste("                                         H2O F1 Score: ", h2oF1, "\n", sep = ""))
    Log.info(paste("                ========= LiblineaR Performance ===========\n", sep = ""))
    Log.info(paste("LiblineaR AUC (performance(prediction(predictions,actual))): ", LibRAUC[[1]], "\n", sep = ""))
    Log.info(paste("                        LiblineaR Precision (tp / (tp + fp): ", LibRPrecision, "\n", sep = ""))
    Log.info(paste("                           LiblineaR Recall (tp / (tp + fn): ", LibRRecall, "\n", sep = ""))
    Log.info(paste("                                         LiblineaR F1 Score: ", LibRF1, "\n", sep = ""))
    return(list(h2o.m,LibR.m));
  }

  compareCoefs <- function(h2o, libR) {
    Log.info("
            Comparing the L1-regularized LR coefficients (should be close in magnitude)
            Expect a sign flip because modeling against log(../(1-p)) vs log((1-p)/p).
            Note that this is not the issue of consistency of signs between odds ratios
            and coefficients.\n")
    

    cat("\n H2O betas: ", h2o@model$coefficients_table$coefficients, "\n")
    cat("\n============================================== \n")
    cat("\n LiblineaR betas: ", libR$W, "\n")
    cat("\n============================================== \n")
    rms_diff <- sqrt(sum(abs(h2o@model$coefficients_table$coefficients) - abs(libR$W))**2)
    Log.info(paste("RMS of the absolute difference in the sets of coefficients is: ", rms_diff, "\n", sep = ""))
    #print(all.equal(abs(as.vector(h2o@model$coefficients)), abs(as.vector(libR$W))), "\n")
  }
  
  Log.info("Importing Airlines test/train data...\n")
  exdir         <- sandbox()
#  exdir <- "/Users/spencer/0xdata/h2o-3/h2o-r/tests/testdir_algos/glm/Rsandbox_runit_GLM_libR_airlines.R/"
#  airlinesTrain <- "/Users/spencer/0xdata/h2o-3/smalldata/airlines/AirlinesTrain.csv.zip"   #locate("smalldata/airlines/AirlinesTrain.csv.zip")
#  airlinesTest  <- "/Users/spencer/0xdata/h2o-3/smalldata/airlines/AirlinesTest.csv.zip"  #locate("smalldata/airlines/AirlinesTest.csv.zip")
  airlinesTrain <- locate("smalldata/airlines/AirlinesTrain.csv.zip")
  airlinesTest  <- locate("smalldata/airlines/AirlinesTest.csv.zip")
  aTrain        <- na.omit(read.zip(zipfile = airlinesTrain, exdir = exdir))
  aTest         <- na.omit(read.zip(zipfile = airlinesTest,  exdir = exdir))
  trainhex      <- h2o.uploadFile(paste(exdir, "/AirlinesTrain.csv", sep = ""), "aTrain.hex")
  testhex       <- h2o.uploadFile(paste(exdir, "/AirlinesTest.csv",  sep=""), "aTest.hex")
  
  print(trainhex)

  Log.info("Mapping column IsDepDelayed_REC from {-1,1} to {0,1}...\n")
  aTrain$IsDepDelayed_REC   <- as.numeric(aTrain$IsDepDelayed_REC == 1)
  aTest$IsDepDelayed_REC    <- as.numeric(aTest$IsDepDelayed_REC == 1)
  trainhex$IsDepDelayed_REC <- trainhex$IsDepDelayed_REC == 1
  trainhex$IsDepDelayed_REC <- as.factor(trainhex$IsDepDelayed_REC)
  testhex$IsDepDelayed_REC  <- testhex$IsDepDelayed_REC == 1
   
  #xTrain  <- scale(model.matrix(IsDepDelayed_REC ~., aTrain[,-11])[,-1])
  xTrain  <- scale(data.frame(aTrain$DepTime, aTrain$ArrTime, aTrain$Distance))
  yTrain  <- aTrain[,12]
  #xTest   <- model.matrix(IsDepDelayed_REC ~., aTest[-11])[,-1]
  xTest   <- scale(data.frame(aTest$DepTime, aTest$ArrTime, aTest$Distance))
  yTest   <- aTest[,12]
  train <- xTrain
  trainLabels <- yTrain
  test <- xTest
  testLabels <- yTest

  models  <- L1logistic(xTrain,yTrain,xTest,yTest,trainhex,testhex)
  compareCoefs(models[[1]], models[[2]])
  
  
}

doTest("LiblineaR Test: Airlines", test.LiblineaR.airlines)

