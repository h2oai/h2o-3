setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
####### This tests deviance for poisson, tweedie and gamma distributions in gbm by comparing results with R ######



test <- function() {
	
	Hexpend =  read.csv(h2oTest.locate("smalldata/glm_test/HealthExpend.csv"))
	MEPS=subset(Hexpend,EXPENDIP>0)
	#MEPSgamma = gbm(formula = EXPENDIP~COUNTIP+AGE+GENDER+factor(RACE)+factor(REGION)+factor(EDUC)+
    #          factor(PHSTAT)+MNHPOOR+ANYLIMIT+factor(INCOME)+insure,distribution =  "gamma",verbose=T,
    #         data=MEPS,n.trees = 1,interaction.depth = 1,n.minobsinnode = 10,shrinkage = 1,bag.fraction = 1,train.fraction = 1)

	hdata = as.h2o(MEPS,destination_frame = "MEPS")
	myX = c("COUNTIP","AGE", "GENDER", "RACE" ,"REGION", "EDUC","PHSTAT","MNHPOOR" ,"ANYLIMIT","INCOME","insure")
	hdata$RACE = as.factor(hdata$RACE)
	hdata$REGION = as.factor(hdata$REGION)
	hdata$EDUC = as.factor(hdata$EDUC)
	hdata$PHSTAT = as.factor(hdata$PHSTAT)
	hdata$INCOME = as.factor(hdata$INCOME)
	hh = h2o.gbm(x = myX,y = "EXPENDIP",training_frame = hdata,distribution =  "gamma",validation_frame = hdata,
             ntrees = 1,max_depth = 1,min_rows = 10,learn_rate = 1)
	print("gamma")
	#expect_equal(hh@model$init_f,MEPSgamma$initF)
	#expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,MEPSgamma$train.error)
	expect_equal(hh@model$init_f,9.460660441)
	expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,20.53711278)
	expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,hh@model$validation_metrics@metrics$mean_residual_deviance)
	
	#MEPStweedie=gbm(EXPENDIP~COUNTIP+AGE+insure,distribution =  "tweedie",verbose = T,
    #            data=MEPS,n.trees = 1,interaction.depth = 1,n.minobsinnode = 10,shrinkage = 1,bag.fraction = 1,train.fraction = 1)
	myX = c("COUNTIP","AGE", "insure")
	hh = h2o.gbm(x = myX,y = "EXPENDIP",training_frame = hdata,distribution =  "tweedie",validation_frame = hdata,
             ntrees = 1,max_depth = 1,min_rows = 10,learn_rate = 1)
	print("tweedie")
	#expect_equal(hh@model$init_f,MEPStweedie$initF)
	#expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,MEPStweedie$train.error)
	expect_equal(hh@model$init_f,9.460660441)
	expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,149.4331681)
	expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,hh@model$validation_metrics@metrics$mean_residual_deviance)
	
	fre = h2o.uploadFile(h2oTest.locate("smalldata/glm_test/freMTPL2freq.csv.zip"),destination_frame = "fre")
	fre$VehPower = as.factor(fre$VehPower)
	#fren = as.data.frame(fre)
	#fren$VehPower = as.factor(fren$VehPower)
	#gg = gbm(formula = ClaimNb~ Area +as.factor(VehPower)+ VehAge+ DrivAge+ BonusMalus+ VehBrand + VehGas +Density +Region,distribution =  "poisson",verbose = T,
    #     weights=Exposure,data = fren,n.trees = 1,interaction.depth = 1,n.minobsinnode = 1,shrinkage = 1,bag.fraction = 1,train.fraction = 1)
	#pr = predict(gg,newdata = fren,type = "response")
	hh = h2o.gbm(x = 4:12,y = "ClaimNb",training_frame = fre,distribution =  "poisson",validation_frame = fre,
             weights_column = "Exposure",ntrees = 1,max_depth = 1,min_rows = 1,learn_rate = 1)
	#ph = as.vector(as.data.frame(h2o.predict(hh,newdata = fre)))
	print("poisson")
	#expect_equal(hh@model$init_f,gg$initF)
	#expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,gg$train.error)
	expect_equal(hh@model$init_f,-2.40404516)
	expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,0.610489769)
	expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,hh@model$validation_metrics@metrics$mean_residual_deviance)

	
}
h2oTest.doTest("GBM residual deviance Test: GBM deviance for poisson/gamma/tweedie distributions", test)
