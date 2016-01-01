setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
####### This tests gamma distribution in gbm by comparing results with R ######



test <- function(h) {

	cancar = read.csv(file =h2oTest.locate("smalldata/glm_test/cancar_logIn.csv"),header = T) 

	cancar$Merit = as.factor(cancar$Merit) 
	cancar$Class = as.factor(cancar$Class) 
	response = cancar$Cost/cancar$Claims 
	cancar = data.frame(cancar,response) 
	#gg = gbm(formula = response~ Merit + Class,data = cancar,distribution = "gamma", 
    #     n.trees = 20,interaction.depth = 1,n.minobsinnode = 1,shrinkage = 1,bag.fraction = 1,train.fraction = 1) 
	#pr = predict(gg,newdata = cancar,type = "response") 
 
	hcancar = as.h2o(cancar,destination_frame = "hcancar") 
	hh = h2o.gbm(x = 1:2,y = "response",training_frame = hcancar,distribution = "gamma", 
             ntrees = 20,max_depth = 1,min_rows = 1,learn_rate = 1) 
	ph = as.data.frame(h2o.predict(hh,newdata = hcancar))
 
	#expect_equal(gg$initF,hh@model$init_f,tolerance = 1e-6)
	#expect_equal(min(pr),min(ph[,1]),tolerance = 1e-6)
	#expect_equal(max(pr),max(ph[,1]),tolerance = 1e-6)
	#expect_equal(mean(pr),mean(ph[,1]),tolerance = 1e-6)
	expect_equal(-1.182827,hh@model$init_f,tolerance = 1e-6)
	expect_equal( 0.262575,min(ph[,1]),tolerance = 1e-4)
	expect_equal(0.3581305,max(ph[,1]),tolerance = 1e-4)
	expect_equal( 0.3064352,mean(ph[,1]),tolerance = 1e-5)
	
	
}
h2oTest.doTest("GBM gamma Test: GBM test for gamma distribution", test)
