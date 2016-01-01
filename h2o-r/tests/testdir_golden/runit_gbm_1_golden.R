setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.gbmMSEgauss.golden <- function() {
	
#Import data: 
h2oTest.logInfo("Importing smtrees data...") 
smtreesH2O <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/smtrees.csv"), destination_frame="smtreesH2O")
smtreesR <- read.csv(h2oTest.locate("smalldata/gbm_test/smtrees.csv"))

h2oTest.logInfo("Test H2O generation of MSE for GBM")
fith2o <- h2o.gbm(x=c("girth", "height"), y="vol", ntrees=3, max_depth=1, distribution="gaussian", min_rows=2, learn_rate=.1, training_frame=smtreesH2O)

#Reported MSE from H2O through R
err <- as.data.frame(fith2o@model$scoring_history$training_MSE)
REPMSE <- err[4,]

#MSE Calculated by hand From H2O predicted values
pred <- as.data.frame(predict(fith2o, newdata=smtreesH2O))
diff <- pred-smtreesR[,4]
diffsq <- diff^2
EXPMSE <- mean(diffsq)

h2oTest.logInfo("Print model MSE... \n")
h2oTest.logInfo(paste("Length of H2O MSE Vec: ", length(fith2o@model$scoring_history$training_MSE),      "\t\t", "Expected Length   : ", 4))
h2oTest.logInfo(paste("H2O Reported MSE  : ", REPMSE, "\t\t", "R Expected MSE   : ", EXPMSE))

h2oTest.logInfo("Compare model statistics in R to model statistics in H2O")
expect_equal(length(fith2o@model$scoring_history$training_MSE), 4)
expect_equal(fith2o@model$init_f, mean(smtreesH2O$vol), tolerance=1e-4) ## check the intercept term
expect_equal(REPMSE, EXPMSE, tolerance=1e-4)
expect_equal(REPMSE>0, TRUE);


}

h2oTest.doTest("GBM Test: Golden GBM - MSE for GBM Regression", test.gbmMSEgauss.golden)
