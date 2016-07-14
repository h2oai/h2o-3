setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.gbmRMSEgauss.golden <- function() {
	
#Import data: 
Log.info("Importing smtrees data...") 
smtreesH2O <- h2o.uploadFile(locate("smalldata/gbm_test/smtrees.csv"), destination_frame="smtreesH2O")
smtreesR <- read.csv(locate("smalldata/gbm_test/smtrees.csv"))

Log.info("Test H2O generation of RMSE for GBM")
fith2o <- h2o.gbm(x=c("girth", "height"), y="vol", ntrees=3, max_depth=1, distribution="gaussian", min_rows=2, learn_rate=.1, training_frame=smtreesH2O)

#Reported RMSE from H2O through R
err <- as.data.frame(fith2o@model$scoring_history$training_rmse)
REPRMSE <- err[4,]

#RMSE Calculated by hand From H2O predicted values
pred <- as.data.frame(predict(fith2o, newdata=smtreesH2O))
diff <- pred-smtreesR[,4]
diffsq <- diff^2
EXPRMSE <- sqrt(mean(diffsq))

Log.info("Print model RMSE... \n")
Log.info(paste("Length of H2O RMSE Vec: ", length(fith2o@model$scoring_history$training_rmse),      "\t\t", "Expected Length   : ", 4))
Log.info(paste("H2O Reported RMSE  : ", REPRMSE, "\t\t", "R Expected RMSE   : ", EXPRMSE))

Log.info("Compare model statistics in R to model statistics in H2O")
expect_equal(length(fith2o@model$scoring_history$training_rmse), 4)
expect_equal(fith2o@model$init_f, mean(smtreesH2O$vol), tolerance=1e-4) ## check the intercept term
expect_equal(REPRMSE, EXPRMSE, tolerance=1e-4)
expect_equal(REPRMSE>0, TRUE);


}

doTest("GBM Test: Golden GBM - RMSE for GBM Regression", test.gbmRMSEgauss.golden)
