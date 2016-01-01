setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
####### This tests gamma distribution w weights in gbm by comparing results with R ######



test <- function() {
	
	htable  = h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/moppe.csv"))
	htable$premiekl = as.factor(htable$premiekl)
	htable$moptva = as.factor(htable$moptva)
	htable$zon = as.factor(htable$zon)
	#gg = gbm(formula = medskad ~ premiekl + moptva + zon,data = table.1.2,distribution = "gamma", weights = table.1.2$antskad ,
    #     n.trees = 20,interaction.depth = 1,n.minobsinnode = 1,shrinkage = 1,bag.fraction = 1,train.fraction = 1)
	#pr = predict(gg,newdata = table.1.2,type = "response")
	#htable= as.h2o(table.1.2,destination_frame = "htable")
	hh = h2o.gbm(x = 1:3,y = "medskad",training_frame = htable,distribution = "gamma",weights_column = "antskad",
             ntrees = 20,max_depth = 1,min_rows = 1,learn_rate = 1)
	ph = as.vector(as.data.frame(h2o.predict(hh,newdata = htable)))
	
	#expect_equal(gg$initF,hh@model$init_f,tolerance = 1e-6)
	#expect_equal(min(pr),min(ph[,1]),tolerance = 1e-6)
	#expect_equal(max(pr),max(ph[,1]),tolerance = 1e-6)
	#expect_equal(mean(pr),mean(ph[,1]),tolerance = 1e-6)
	expect_equal(8.804447,hh@model$init_f,tolerance = 1e-6)
	expect_equal(3751.01,min(ph[,1]),tolerance = 1e-4)
	expect_equal(15298.87,max(ph[,1]),tolerance = 1e-4)
	expect_equal( 8121.98,mean(ph[,1]),tolerance = 1e-4)
	
	
}
h2oTest.doTest("GBM weight Test: GBM w/ weight for gamma distribution", test)
