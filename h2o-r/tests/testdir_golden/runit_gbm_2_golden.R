setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.gbmMAEgauss.golden <- function() {
	
#Import data: 
Log.info("Importing smtrees data...") 
smtreesH2O <- h2o.uploadFile(locate("smalldata/gbm_test/smtrees.csv"), destination_frame="smtreesH2O")
smtreesR <- read.csv(locate("smalldata/gbm_test/smtrees.csv"))

Log.info("Test H2O generation of MAE for GBM")
fith2o <- h2o.gbm(x=c("girth", "height"), y="vol", ntrees=3, max_depth=1, distribution="gaussian", min_rows=2, learn_rate=.1, training_frame=smtreesH2O)

#Reported mae from H2O through R
err <- as.data.frame(fith2o@model$scoring_history$training_mae)
REPmae <- err[4,]

#mae Calculated by hand From H2O predicted values
pred <- as.data.frame(predict(fith2o, newdata=smtreesH2O))
diff <- pred-smtreesR[,4]
diff_abs <- abs(diff)
print(diff_abs)
EXPmae <- mean(diff_abs$predict)

Log.info("Print model mae... \n")
Log.info(paste("Length of H2O MAE Vec: ", length(fith2o@model$scoring_history$training_mae),      "\t\t", "Expected Length   : ", 4))
Log.info(paste("H2O Reported MAE  : ", REPmae, "\t\t", "R Expected MAE   : ", EXPmae))

Log.info("Compare model statistics in R to model statistics in H2O")
expect_equal(length(fith2o@model$scoring_history$training_mae), 4)
expect_equal(fith2o@model$init_f, mean(smtreesH2O$vol), tolerance=1e-4) ## check the intercept term
expect_equal(REPmae, EXPmae, tolerance=1e-4)
expect_equal(REPmae>0, TRUE);


}

doTest("GBM Test: Golden GBM - MAE for GBM Regression", test.gbmMAEgauss.golden)
