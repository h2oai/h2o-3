setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.glm2ProstateAUC.golden <- function() {
	
    #Import data:
    h2oTest.logInfo("Importing Benign data...")
    prostateH2O<- h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame="cuseH2O")
    prostateR<- read.csv(h2oTest.locate("smalldata/logreg/prostate.csv"), header=T)
    
 h2oTest.logInfo("Run matching models in R and H2O")
    fitH2O<- h2o.glm(y="CAPSULE", x=c("AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"), training_frame=prostateH2O, family="binomial", lambda=0, alpha=0, nfolds=0, standardize=F)
    fitR<- glm(CAPSULE ~ AGE + RACE + DPROS + DCAPS + PSA + VOL + GLEASON, family=binomial, data=prostateR)
     prostateR$predsR<- predict.glm(fitR, newdata=NULL, type="response")
     preds2R<- prediction(prostateR$predsR, labels=prostateR$CAPSULE)
     auc<- performance(preds2R, measure="AUC")
     aucR<- auc@y.values[[1]]
     aucH<- fitH2O@model$training_metrics@metrics$AUC

         h2oTest.logInfo("Print model statistics for R and H2O... \n")
    h2oTest.logInfo(paste("H2O Deviance  : ", fitH2O@model$training_metrics@metrics$residual_deviance,      "\t\t", "R Deviance   : ", fitR$deviance))
    h2oTest.logInfo(paste("H2O Null Dev  : ", fitH2O@model$training_metrics@metrics$null_deviance, "\t\t", "R Null Dev   : ", fitR$null.deviance))
    h2oTest.logInfo(paste("H2O residul df: ", fitH2O@model$training_metrics@metrics$residual_degrees_of_freedom,    "\t\t\t\t", "R residual df: ", fitR$df.residual))
    h2oTest.logInfo(paste("H2O null df   : ", fitH2O@model$training_metrics@metrics$null_degrees_of_freedom,       "\t\t\t\t", "R null df    : ", fitR$df.null))
    h2oTest.logInfo(paste("H2O AIC       : ", fitH2O@model$training_metrics@metrics$AIC,           "\t\t", "R AIC        : ", fitR$aic))
    h2oTest.logInfo(paste("H2O AUC       : ", aucH,       "\t\t\t\t", "R AUC    : ", aucR))

    h2oTest.logInfo("Compare model statistics in R to model statistics in H2O")
    expect_equal(fitH2O@model$training_metrics@metrics$null_deviance, fitR$null.deviance, tolerance = 0.01)
    expect_equal(fitH2O@model$training_metrics@metrics$residual_deviance, fitR$deviance, tolerance = 0.01)
    expect_equal(fitH2O@model$training_metrics@metrics$residual_degrees_of_freedom, fitR$df.residual, tolerance = 0.01)
    expect_equal(fitH2O@model$training_metrics@metrics$null_degrees_of_freedom, fitR$df.null, tolerance = 0.01)
    expect_equal(fitH2O@model$training_metrics@metrics$AIC, fitR$aic, tolerance = 0.01)
    expect_equal(aucR, aucH, tolerance=0.05)
    
    
}

h2oTest.doTest("GLM Test: GLM2 - ProstateAUC", test.glm2ProstateAUC.golden)

