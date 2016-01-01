setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


library(LiblineaR)
library(ROCR)

test.LiblineaR <- function() {
  L1logistic <- function(train,trainLabels,test,testLabels,trainhex,testhex) {
    h2oTest.logInfo("Using default parameters for LiblineaR: \n")
    h2oTest.logInfo("   type =    0: Logistic Regression L2-Regularized\n")
    h2oTest.logInfo("   cost =  100: Cost of constraints parameter\n")
    h2oTest.logInfo("epsilon = 1E-2: Tolerance of termination criterion\n")
    h2oTest.logInfo("  cross =    0: No kfold cross-validation\n")
    LibR.m      <- LiblineaR(train, trainLabels,type=0, epsilon=1E-2, cost=100) #cost= 1../ (34 * 7))
    LibRpreds   <- predict(LibR.m, test, proba=1, decisionValues=TRUE)
    LibRCM      <- table(testLabels, LibRpreds$predictions)
    
    LibRPrecision <- LibRCM[1]/ (LibRCM[1] + LibRCM[3])
    LibRRecall    <- LibRCM[1]/ (LibRCM[1] + LibRCM[2])
    LibRF1        <- 2 * (LibRPrecision * LibRRecall)/ (LibRPrecision + LibRRecall)
    LibRAUC       <- performance(prediction(as.numeric(LibRpreds$predictions), testLabels), measure = "auc")@y.values
    
    h2oTest.logInfo("Using these parameters for H2O: \n")
    h2oTest.logInfo(" family = 'binomial': Logistic Regression\n")
    h2oTest.logInfo(" lambda =      1/700: Shrinkage Parameter\n")
    h2oTest.logInfo("  alpha =        0.0: Elastic Net Parameter\n")
    h2oTest.logInfo("beta_epsilon =  E-02: Tolerance of termination criterion\n")
    h2oTest.logInfo(" nfolds =          1: No kfold cross-validation\n")
    h2o.m <- h2o.glm(x              = c("GLEASON","DPROS","PSA","DCAPS","AGE","RACE","VOL"), 
                     y              = "CAPSULE",
                     training_frame = trainhex,
                     family         = "binomial",
                     nfolds         = 1,
                     lambda         = 1/ (7 * 100), #700,
                     alpha          = 0.0,
                     beta_epsilon   = 1E-2)
    
    h2op         <- predict(h2o.m, testhex)
    h2opreds     <- as.numeric(as.character(as.data.frame(h2op)[,1]))
    h2oCM        <- table(testLabels, h2opreds)
    
    h2oPrecision <- h2oCM[1]/ (h2oCM[1] + h2oCM[3])
    h2oRecall    <- h2oCM[1]/ (h2oCM[1] + h2oCM[2])
    h2oF1        <- 2 * (h2oPrecision * h2oRecall)/ (h2oPrecision + h2oRecall)
    h2oAUC       <- performance(prediction(h2opreds, testLabels), measure = "auc")@y.values
    
    h2oTest.logInfo("                ============= H2O Performance =============\n")
    h2oTest.logInfo(paste("H2O AUC (performance(prediction(predictions,actual))): ", h2oAUC[[1]], "\n", sep = ""))
    h2oTest.logInfo(paste("                        H2O Precision (tp../ (tp + fp): ", h2oPrecision, "\n", sep = ""))
    h2oTest.logInfo(paste("                           H2O Recall (tp../ (tp + fn): ", h2oRecall, "\n", sep =""))
    h2oTest.logInfo(paste("                                         H2O F1 Score: ", h2oF1, "\n", sep = ""))
    h2oTest.logInfo("                ========= LiblineaR Performance ===========\n")
    h2oTest.logInfo(paste("LiblineaR AUC (performance(prediction(predictions,actual))): ", LibRAUC[[1]], "\n", sep =""))
    h2oTest.logInfo(paste("                        LiblineaR Precision (tp../ (tp + fp): ", LibRPrecision, "\n", sep =""))
    h2oTest.logInfo(paste("                           LiblineaR Recall (tp../ (tp + fn): ", LibRRecall, "\n", sep = ""))
    h2oTest.logInfo(paste("                                         LiblineaR F1 Score: ", LibRF1, "\n", sep ="")) 
    h2oTest.logInfo("                ========= H2O & LibR coeff. comparison ====\n")
    cat("              ", format(names(h2o.m@model$coefficients_table$coefficients),width=10,justify='right'), "\n")
    cat(" H2O coefficients: ", h2o.m@model$coefficients_table$coefficients, "\n")
    cat("LibR coefficients: ", LibR.m$W, "\n")
    return(list(h2o.m,LibR.m))
  }
  
  L2logistic <- function(train,trainLabels,test,testLabels,trainhex,testhex) {
    h2oTest.logInfo("Using these parameters for LiblineaR: \n")
    h2oTest.logInfo("   type =                      0: Logistic Regression L2-Regularized\n")
    h2oTest.logInfo("   cost =                     10: Cost of constraints parameter\n")
    h2oTest.logInfo("epsilon =                   1E-2: Tolerance of termination criterion\n")
    h2oTest.logInfo("  cross =                      0: No kfold cross-validation\n")
    LibR.m      <- LiblineaR(train, trainLabels, type=0, epsilon=1E-2,cost=10)
    LibRpreds  <- predict(LibR.m, test, proba=1, decisionValues=TRUE)
    LibRCM <- table(testLabels, LibRpreds$predictions)
    
    LibRPrecision <- LibRCM[1]/ (LibRCM[1] + LibRCM[3])
    LibRRecall    <- LibRCM[1]/ (LibRCM[1] + LibRCM[2])
    LibRF1        <- 2 * (LibRPrecision * LibRRecall)/ (LibRPrecision + LibRRecall)
    LibRAUC       <- performance(prediction(as.numeric(LibRpreds$predictions), testLabels), measure = "auc")@y.values
    
    h2oTest.logInfo("Using these parameters for H2O: \n")
    h2oTest.logInfo(" family = 'binomial': Logistic Regression\n")
    h2oTest.logInfo(" lambda =      1E-03: Shrinkage Parameter\n")
    h2oTest.logInfo("  alpha =        0.0: Elastic Net Parameter\n")
    h2oTest.logInfo("epsilon =      1E-02: Tolerance of termination criterion\n")
    h2oTest.logInfo(" nfolds =          1: No kfold cross-validation")
    h2o.m <- h2o.glm(x            = c("GLEASON","DPROS","PSA","DCAPS","AGE","RACE","VOL"), 
                     y            = "CAPSULE", 
                     data         = trainhex, 
                     family       = "binomial",
                     nfolds       = 1, 
                     lambda       = 1/70,
                     alpha        = 0.00,
                     epsilon = 1E-2)
    
    h2op     <- h2o.predict(h2o.m, testhex)
    h2opreds     <- as.numeric(as.character(as.data.frame(h2op)[,1]))
    h2oCM    <- table(testLabels, h2opreds)
    
    h2oPrecision <- h2oCM[1]/ (h2oCM[1] + h2oCM[3])
    h2oRecall    <- h2oCM[1]/ (h2oCM[1] + h2oCM[2])
    h2oF1        <- 2 * (h2oPrecision * h2oRecall)/ (h2oPrecision + h2oRecall)
    h2oAUC       <- performance(prediction(h2opreds, testLabels), measure = "auc")@y.values
    
    h2oTest.logInfo("                ============= H2O Performance =============\n")
    h2oTest.logInfo(paste("H2O AUC (performance(prediction(predictions,actual))): ", h2oAUC[[1]], "\n",sep=""))
    h2oTest.logInfo(paste("                        H2O Precision (tp../ (tp + fp): ", h2oPrecision, "\n", sep=""))
    h2oTest.logInfo(paste("                           H2O Recall (tp../ (tp + fn): ", h2oRecall, "\n", sep=""))
    h2oTest.logInfo(paste("                                         H2O F1 Score: ", h2oF1, "\n", sep=""))
    h2oTest.logInfo(paste("                ========= LiblineaR Performance ===========\n", sep =""))
    h2oTest.logInfo(paste("LiblineaR AUC (performance(prediction(predictions,actual))): ", LibRAUC[[1]], "\n", sep =""))
    h2oTest.logInfo(paste("                        LiblineaR Precision (tp../ (tp + fp): ", LibRPrecision, "\n", sep =""))
    h2oTest.logInfo(paste("                           LiblineaR Recall (tp../ (tp + fn): ", LibRRecall, "\n", sep = ""))
    h2oTest.logInfo(paste("                                         LiblineaR F1 Score: ", LibRF1, "\n", sep = ""))
    h2oTest.logInfo(paste("                 ========= H2O & LibR coeff. comparison ===\n"))
    h2oTest.logInfo("              ", format(names(h2o.m@model$coefficients_table$coefficients),width=10,justify='right'), "\n")
    cat(" H2O coefficients: ", h2o.m@model$coefficients_table$coefficients, "\n",sep = "")
    cat("LibR coefficients: ", LibR.m$W, "\n", sep = "")

    return(list(h2o.m,LibR.m))
  }
  
  compareCoefs <- function(h2o, libR) {
    h2oTest.logInfo("
            Comparing the L1-regularized LR coefficients (should be close in magnitude)
            Expect a sign flip because modeling against log(../(1-p)) vs log((1-p)/p).
            Note that this is not the issue of consistency of signs between odds ratios
            and coefficients.\n")

    h2oTest.logInfo("                ========= H2O & LibR coeff. comparison ===\n")
    cat("              ", format(names(h2o@model$coefficients_table$coefficients),width=10,justify='right'), "\n")
    cat(" H2O coefficients: ", h2o@model$coefficients_table$coefficients, "\n")
    cat("LibR coefficients: ", libR$W, "\n")
    rms_diff <- sqrt(sum(abs(h2o@model$coefficients_table$coefficients) - abs(libR$W))**2)
    h2oTest.logInfo(paste("RMS of the absolute difference in the sets of coefficients is: ", rms_diff, "\n", sep = ""))
    h2oTest.logInfo(paste(all.equal(abs(as.vector(h2o@model$coefficients_table$coefficients)), abs(as.vector(libR$W))), "\n", sep = ""))
  }

  h2oTest.logInfo("Importing prostate test/train data...\n")
  prostate.train.hex <- h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate_train.csv"), "pTrain.hex")
  prostate.test.hex  <- h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate_test.csv"), "pTest.hex")
  prostate.train.hex$CAPSULE <- as.factor(prostate.train.hex$CAPSULE)
  prostate.test.hex$CAPSULE <- as.factor(prostate.test.hex$CAPSULE)
  prostate.train.dat <- read.csv(h2oTest.locate("smalldata/logreg/prostate_train.csv")) #head(prostate.train.hex,nrow(prostate.train.hex))
  prostate.test.dat  <- read.csv(h2oTest.locate("smalldata/logreg/prostate_test.csv")) #head(prostate.test.hex,nrow(prostate.test.hex))
  xTrain             <- prostate.train.dat[,-1]
  yTrain             <- factor(prostate.train.dat[,1])
  xTest              <- prostate.test.dat[,-1]
  yTest              <- factor(prostate.test.dat[,1])
  models             <- L1logistic(xTrain,yTrain,xTest,yTest,prostate.train.hex,prostate.test.hex)
  #models2            <- L2logistic(xTrain,yTrain,xTest,yTest,prostate.train.hex,prostate.test.hex)
  compareCoefs(models[[1]], models[[2]])
  
}

h2oTest.doTest("LiblineaR Test: Prostate", test.LiblineaR)

