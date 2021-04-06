setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure infogram works with validation dataset and cross-validation
infogramUIUCVCV <- function() {
    bhexFV <- h2o.importFile(locate("smalldata/admissibleml_test/taiwan_credit_card_uci.csv"))
    bhexFV["default payment next month"]<- h2o.asfactor(bhexFV["default payment next month"])
    Y <- "default payment next month"
    X <- c("LIMIT_BAL", "EDUCATION", "MARRIAGE",
           "PAY_0", "PAY_2", "PAY_3", "PAY_4", "PAY_5", "PAY_6", "BILL_AMT1", "BILL_AMT2", "BILL_AMT3", "BILL_AMT4",
           "BILL_AMT5", "BILL_AMT6", "PAY_AMT1", "PAY_AMT2", "PAY_AMT3", "PAY_AMT4", "PAY_AMT5", "PAY_AMT6")

    Log.info("Building the model")
    split = h2o.splitFrame(data=bhexFV,ratios=.8)
    train = h2o.assign(split[[1]],key="train")
    test = h2o.assign(split[[2]],key="test")
    
    infogramModel <- h2o.infogram(y=Y, x=X, training_frame=train,  seed=12345, top_n_features=50, protected_columns = c("SEX", "AGE"))
    infogramModelV <- h2o.infogram(y=Y, x=X, training_frame=train,  validation_frame=test, seed=12345, top_n_features=50, protected_columns = c("SEX", "AGE"))
    infogramModelCV <- h2o.infogram(y=Y, x=X, training_frame=train,  nfolds=2, seed=12345, top_n_features=50, protected_columns = c("SEX", "AGE"))
    infogramModelVCV <- h2o.infogram(y=Y, x=X, training_frame=train,  validation_frame=test, nfolds=2, seed=12345, top_n_features=50, protected_columns = c("SEX", "AGE"))
    
    Log.info("compare rel cmi from training dataset")
    relCMITrain <- infogramModel@admissible_score
    relCMITrainV <- infogramModelV@admissible_score
    relCMITrainCV <- infogramModelCV@admissible_score
    relCMITrainVCV <- infogramModelVCV@admissible_score
    compareFrames(relCMITrain, relCMITrainV, prob=1.0)
    compareFrames(relCMITrainCV, relCMITrainVCV, prob=1.0)
    compareFrames(relCMITrain, relCMITrainVCV, prob=1.0)
    
    Log.info("compare rel cmi from validation dataset")
    relCMIValidV <- infogramModelV@admissible_score_valid
    relCMIValidVCV <- infogramModelVCV@admissible_score_valid
    compareFrames(relCMIValidV, relCMIValidVCV)
    
    Log.info("compare rel cmi from cross-validation holdout")
    relCMICVCV <- infogramModelCV@admissible_score_xval
    relCMICVVCV <- infogramModelVCV@admissible_score_xval
    compareFrames(relCMICVCV, relCMICVVCV)
}

doTest("Infogram: UIUC data fair infogram with validation dataset and cross-validation", infogramUIUCVCV)
