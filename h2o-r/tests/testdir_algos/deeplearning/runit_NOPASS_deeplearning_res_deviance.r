####### This tests deviance for poisson, tweedie and gamma distributions in deeplearing by comparing with expected results ######
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test <- function(h) {
	Hexpend =  read.csv(locate("smalldata/glm_test/HealthExpend.csv"))
	MEPS=subset(Hexpend,EXPENDIP>0)
	hdata = as.h2o(MEPS,destination_frame = "MEPS")
	hdata$RACE = as.factor(hdata$RACE)
	hdata$REGION = as.factor(hdata$REGION)
	hdata$EDUC = as.factor(hdata$EDUC)
	hdata$PHSTAT = as.factor(hdata$PHSTAT)
	hdata$INCOME = as.factor(hdata$INCOME)
	
	print("gamma")
	myX = c("COUNTIP","AGE", "GENDER", "RACE" ,"REGION", "EDUC","PHSTAT","MNHPOOR" ,"ANYLIMIT","INCOME","insure")
	hh = h2o.deeplearning(x = myX,y = "EXPENDIP",training_frame = hdata,hidden = c(25,25),epochs = 100,
                      train_samples_per_iteration = -1,validation_frame = hdata,activation = "Tanh",distribution = "gamma")
	pr = as.data.frame(h2o.predict(hh,newdata = hdata))
	pr = log(pr)
	mean_deviance = (sum(MEPS$EXPENDIP*exp(-pr[,1])+pr[,1])*2)/157
	expect_equal(mean_deviance,hh@model$training_metrics@metrics$mean_residual_deviance)
	expect_equal(mean_deviance,hh@model$validation_metrics@metrics$mean_residual_deviance)
	
	print("tweedie")
	myX = c("COUNTIP","AGE", "insure")
	
	hh = h2o.deeplearning(x = myX,y = "EXPENDIP",training_frame = hdata,hidden = c(25,25),epochs = 100,
                      train_samples_per_iteration = -1,validation_frame = hdata,activation = "Tanh",distribution = "tweedie")
	pr = as.data.frame(h2o.predict(hh,newdata = hdata))
	pr = log(pr)
	dPower=1.5
	mean_deviance = (sum((MEPS$EXPENDIP^(2.0-dPower)/((1.0-dPower)*(2.0-dPower)) -
   		MEPS$EXPENDIP*exp(pr[,1]*(1.0-dPower))/(1.0-dPower) + exp(pr[,1]*(2.0-dPower))/(2.0-dPower) ))*2) /157  ## tweedie deviance
	expect_equal(mean_deviance,hh@model$training_metrics@metrics$mean_residual_deviance)
	expect_equal(mean_deviance,hh@model$validation_metrics@metrics$mean_residual_deviance)
	
	print("poisson")
	fre = h2o.uploadFile(locate("smalldata/glm_test/freMTPL2freq.csv.zip"),conn = h,destination_frame = "fre")
	fre$VehPower = as.factor(fre$VehPower)
	#fren = as.data.frame(fre)
	#fren$VehPower = as.factor(fren$VehPower)
	hh = h2o.deeplearning(x = 4:12,y = "ClaimNb",training_frame = fre,hidden = c(2,2),epochs = 1,reproducible = T,seed = 12345,
                       train_samples_per_iteration = -1,validation_frame = fre,activation = "Tanh",distribution = "poisson")
	pr = as.data.frame(h2o.predict(hh,newdata = fre))
	pr = log(pr)
	#mean_deviance = (sum(fren$ClaimNb*pr[,1] - exp(pr[,1]))*-2)/678013 
	expect_equal(0.4796307547,hh@model$training_metrics@metrics$mean_residual_deviance)
	expect_equal(0.4796307547,hh@model$validation_metrics@metrics$mean_residual_deviance)
	
	testEnd()
}
doTest("DL residual deviance Test: DL deviance for poisson/gamma/tweedie distributions", test)