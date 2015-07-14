setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

Log.info("Loading LiblineaR and ROCR packages\n")

#if(!"LiblineaR" %in% rownames(installed.packages())) install.packages("LiblineaR")
#if(!"ROCR" %in% rownames(installed.packages())) install.packages("ROCR")

library(LiblineaR)
library(ROCR)

test.LiblineaR <- function() {
  L1logistic <- function(train,trainLabels,test,testLabels,trainhex,testhex) {
    Log.info("Using default parameters for LiblineaR: \n")
    Log.info("   type =    0: Logistic Regression L2-Regularized\n")
    Log.info("   cost =  100: Cost of constraints parameter\n")
    Log.info("epsilon = 1E-2: Tolerance of termination criterion\n")
    Log.info("  cross =    0: No kfold cross-validation\n")
    LibR.m      <- LiblineaR(train, trainLabels,type=0, epsilon=1E-2, cost=100) #cost= 1../ (34 * 7))
    LibRpreds   <- predict(LibR.m, test, proba=1, decisionValues=TRUE)
    LibRCM      <- table(testLabels, LibRpreds$predictions)
    
    LibRPrecision <- LibRCM[1]/ (LibRCM[1] + LibRCM[3])
    LibRRecall    <- LibRCM[1]/ (LibRCM[1] + LibRCM[2])
    LibRF1        <- 2 * (LibRPrecision * LibRRecall)/ (LibRPrecision + LibRRecall)
    LibRAUC       <- performance(prediction(as.numeric(LibRpreds$predictions), testLabels), measure = "auc")@y.values
    
    Log.info("Using these parameters for H2O: \n")
    Log.info(" family = 'binomial': Logistic Regression\n")
    Log.info(" lambda =      1/700: Shrinkage Parameter\n")
    Log.info("  alpha =        0.0: Elastic Net Parameter\n")
    Log.info("beta_epsilon =  E-02: Tolerance of termination criterion\n")
    Log.info(" nfolds =          1: No kfold cross-validation\n")
    h2o.m <- h2o.glm(x              = c("GLEASON","DPROS","PSA","DCAPS","AGE","RACE","VOL"), 
                     y              = "CAPSULE",
                     training_frame = trainhex,
                     family         = "binomial",
                     nfolds         = 1,
                     lambda         = 1/ (7 * 100), #700,
                     alpha          = 0.0,
                     beta_epsilon   = 1E-2)
    
    h2op         <- predict(h2o.m, testhex)
    h2opreds     <- head(h2op, nrow(h2op))
    h2oCM        <- table(testLabels, h2opreds$predict)
    
    h2oPrecision <- h2oCM[1]/ (h2oCM[1] + h2oCM[3])
    h2oRecall    <- h2oCM[1]/ (h2oCM[1] + h2oCM[2])
    h2oF1        <- 2 * (h2oPrecision * h2oRecall)/ (h2oPrecision + h2oRecall)
    h2oAUC       <- performance(prediction(h2opreds$predict, testLabels), measure = "auc")@y.values
    
    Log.info("                ============= H2O Performance =============\n")
    Log.info(paste("H2O AUC (performance(prediction(predictions,actual))): ", h2oAUC[[1]], "\n", sep = ""))
    Log.info(paste("                        H2O Precision (tp../ (tp + fp): ", h2oPrecision, "\n", sep = ""))
    Log.info(paste("                           H2O Recall (tp../ (tp + fn): ", h2oRecall, "\n", sep =""))
    Log.info(paste("                                         H2O F1 Score: ", h2oF1, "\n", sep = ""))
    Log.info("                ========= LiblineaR Performance ===========\n")
    Log.info(paste("LiblineaR AUC (performance(prediction(predictions,actual))): ", LibRAUC[[1]], "\n", sep =""))
    Log.info(paste("                        LiblineaR Precision (tp../ (tp + fp): ", LibRPrecision, "\n", sep =""))
    Log.info(paste("                           LiblineaR Recall (tp../ (tp + fn): ", LibRRecall, "\n", sep = ""))
    Log.info(paste("                                         LiblineaR F1 Score: ", LibRF1, "\n", sep ="")) 
    Log.info("                ========= H2O & LibR coeff. comparison ====\n")
    cat("              ", format(names(h2o.m@model$coefficients_table$coefficients),width=10,justify='right'), "\n")
    cat(" H2O coefficients: ", h2o.m@model$coefficients_table$coefficients, "\n")
    cat("LibR coefficients: ", LibR.m$W, "\n")
    return(list(h2o.m,LibR.m))
  }
  
  L2logistic <- function(train,trainLabels,test,testLabels,trainhex,testhex) {
    Log.info("Using these parameters for LiblineaR: \n")
    Log.info("   type =                      0: Logistic Regression L2-Regularized\n")
    Log.info("   cost =                     10: Cost of constraints parameter\n")
    Log.info("epsilon =                   1E-2: Tolerance of termination criterion\n")
    Log.info("  cross =                      0: No kfold cross-validation\n")
    LibR.m      <- LiblineaR(train, trainLabels, type=0, epsilon=1E-2,cost=10)
    LibRpreds  <- predict(LibR.m, test, proba=1, decisionValues=TRUE)
    LibRCM <- table(testLabels, LibRpreds$predictions)
    
    LibRPrecision <- LibRCM[1]/ (LibRCM[1] + LibRCM[3])
    LibRRecall    <- LibRCM[1]/ (LibRCM[1] + LibRCM[2])
    LibRF1        <- 2 * (LibRPrecision * LibRRecall)/ (LibRPrecision + LibRRecall)
    LibRAUC       <- performance(prediction(as.numeric(LibRpreds$predictions), testLabels), measure = "auc")@y.values
    
    Log.info("Using these parameters for H2O: \n")
    Log.info(" family = 'binomial': Logistic Regression\n")
    Log.info(" lambda =      1E-03: Shrinkage Parameter\n")
    Log.info("  alpha =        0.0: Elastic Net Parameter\n")
    Log.info("epsilon =      1E-02: Tolerance of termination criterion\n")
    Log.info(" nfolds =          1: No kfold cross-validation")
    h2o.m <- h2o.glm(x            = c("GLEASON","DPROS","PSA","DCAPS","AGE","RACE","VOL"), 
                     y            = "CAPSULE", 
                     data         = trainhex, 
                     family       = "binomial",
                     nfolds       = 1, 
                     lambda       = 1/70,
                     alpha        = 0.00,
                     epsilon = 1E-2)
    
    h2op     <- h2o.predict(h2o.m, testhex)
    h2opreds <- head(h2op, nrow(h2op))
    h2oCM    <- table(testLabels, h2opreds$predict)
    
    h2oPrecision <- h2oCM[1]/ (h2oCM[1] + h2oCM[3])
    h2oRecall    <- h2oCM[1]/ (h2oCM[1] + h2oCM[2])
    h2oF1        <- 2 * (h2oPrecision * h2oRecall)/ (h2oPrecision + h2oRecall)
    h2oAUC       <- performance(prediction(h2opreds$predict, testLabels), measure = "auc")@y.values
    
    Log.info("                ============= H2O Performance =============\n")
    Log.info(paste("H2O AUC (performance(prediction(predictions,actual))): ", h2oAUC[[1]], "\n",sep=""))
    Log.info(paste("                        H2O Precision (tp../ (tp + fp): ", h2oPrecision, "\n", sep=""))
    Log.info(paste("                           H2O Recall (tp../ (tp + fn): ", h2oRecall, "\n", sep=""))
    Log.info(paste("                                         H2O F1 Score: ", h2oF1, "\n", sep=""))
    Log.info(paste("                ========= LiblineaR Performance ===========\n", sep =""))
    Log.info(paste("LiblineaR AUC (performance(prediction(predictions,actual))): ", LibRAUC[[1]], "\n", sep =""))
    Log.info(paste("                        LiblineaR Precision (tp../ (tp + fp): ", LibRPrecision, "\n", sep =""))
    Log.info(paste("                           LiblineaR Recall (tp../ (tp + fn): ", LibRRecall, "\n", sep = ""))
    Log.info(paste("                                         LiblineaR F1 Score: ", LibRF1, "\n", sep = ""))
    Log.info(paste("                 ========= H2O & LibR coeff. comparison ===\n"))
    Log.info("              ", format(names(h2o.m@model$coefficients_table$coefficients),width=10,justify='right'), "\n")
    cat(" H2O coefficients: ", h2o.m@model$coefficients_table$coefficients, "\n",sep = "")
    cat("LibR coefficients: ", LibR.m$W, "\n", sep = "")

    return(list(h2o.m,LibR.m))
  }
  
  compareCoefs <- function(h2o, libR) {
    Log.info("
            Comparing the L1-regularized LR coefficients (should be close in magnitude)
            Expect a sign flip because modeling against log(../(1-p)) vs log((1-p)/p).
            Note that this is not the issue of consistency of signs between odds ratios
            and coefficients.\n")

    Log.info("                ========= H2O & LibR coeff. comparison ===\n")
    cat("              ", format(names(h2o@model$coefficients_table$coefficients),width=10,justify='right'), "\n")
    cat(" H2O coefficients: ", h2o@model$coefficients_table$coefficients, "\n")
    cat("LibR coefficients: ", libR$W, "\n")
    rms_diff <- sqrt(sum(abs(h2o@model$coefficients_table$coefficients) - abs(libR$W))**2)
    Log.info(paste("RMS of the absolute difference in the sets of coefficients is: ", rms_diff, "\n", sep = ""))
    Log.info(paste(all.equal(abs(as.vector(h2o@model$coefficients_table$coefficients)), abs(as.vector(libR$W))), "\n", sep = ""))
  }

  Log.info("Importing prostate test/train data...\n")
  prostate.train.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate_train.csv"), "pTrain.hex")
  prostate.test.hex  <- h2o.uploadFile(locate("smalldata/logreg/prostate_test.csv"), "pTest.hex")
  prostate.train.hex$CAPSULE <- as.factor(prostate.train.hex$CAPSULE)
  prostate.test.hex$CAPSULE <- as.factor(prostate.test.hex$CAPSULE)
  prostate.train.dat <- read.csv(locate("smalldata/logreg/prostate_train.csv")) #head(prostate.train.hex,nrow(prostate.train.hex))
  prostate.test.dat  <- read.csv(locate("smalldata/logreg/prostate_test.csv")) #head(prostate.test.hex,nrow(prostate.test.hex))
  xTrain             <- prostate.train.dat[,-1]
  yTrain             <- factor(prostate.train.dat[,1])
  xTest              <- prostate.test.dat[,-1]
  yTest              <- factor(prostate.test.dat[,1])
  models             <- L1logistic(xTrain,yTrain,xTest,yTest,prostate.train.hex,prostate.test.hex)
  #models2            <- L2logistic(xTrain,yTrain,xTest,yTest,prostate.train.hex,prostate.test.hex)
  compareCoefs(models[[1]], models[[2]])
  testEnd()
}

doTest("LiblineaR Test: Prostate", test.LiblineaR)

