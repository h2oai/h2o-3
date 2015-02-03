setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.gbmMSEgauss.golden <- function(H2Oserver) {
	
#Import data: 
Log.info("Importing smtrees data...") 
smtreesH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/gbm_test/smtrees.csv"), key="smtreesH2O")
smtreesR <- read.csv(locate("smalldata/gbm_test/smtrees.csv"))

Log.info("Test H2O generation of MSE for GBM")
fith2o <- h2o.gbm(x=c("girth", "height"), y="vol", ntrees=3, max_depth=1, loss="gaussian", min_rows=2, learn_rate=.1, training_frame=smtreesH2O)

#Reported MSE from H2O through R
err <- as.data.frame(fith2o@model$err)
REPMSE <- err[4,]

#MSE Calculated by hand From H2O predicted values
pred <- as.data.frame(predict(fith2o, newdata=smtreesH2O))
diff <- pred-smtreesR[,4]
diffsq <- diff^2
EXPMSE <- mean(diffsq)

Log.info("Print model MSE... \n")
Log.info(paste("Length of H2O MSE Vec: ", length(fith2o@model$err),      "\t\t", "Expected Length   : ", 4))
Log.info(paste("H2O Reported MSE  : ", REPMSE, "\t\t", "R Expected MSE   : ", EXPMSE))

Log.info("Compare model statistics in R to model statistics in H2O")
expect_equal(length(fith2o@model$err), 4) # 3 errs per for each subforest + one error for empty forest.
expect_equal(REPMSE, EXPMSE, tolerance=1e-4)
expect_equal(REPMSE>0, TRUE);

testEnd()
}

doTest("GBM Test: Golden GBM - MSE for GBM Regression", test.gbmMSEgauss.golden)
