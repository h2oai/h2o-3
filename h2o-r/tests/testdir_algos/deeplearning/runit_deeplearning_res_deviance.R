setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
####### This tests deviance for poisson, tweedie and gamma distributions in deeplearning by comparing with expected results ######



test <- function() {
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
                      train_samples_per_iteration = -1,validation_frame = hdata,activation = "Tanh",distribution = "gamma", score_training_samples=0)
	pr = as.data.frame(h2o.predict(hh,newdata = hdata))
	pr = log(pr)
	mean_deviance = (sum(MEPS$EXPENDIP*exp(-pr[,1])+pr[,1])*2)/157

        #print(mean_deviance)
        #print(hh@model$training_metrics@metrics$mean_residual_deviance)
        #print(hh@model$validation_metrics@metrics$mean_residual_deviance)

	expect_equal(mean_deviance, hh@model$training_metrics@metrics$mean_residual_deviance, tolerance=1e-8)
	expect_equal(mean_deviance, hh@model$validation_metrics@metrics$mean_residual_deviance, tolerance=1e-8)
	

	print("tweedie")
	myX = c("COUNTIP","AGE", "insure")
	
	hh = h2o.deeplearning(x = myX,y = "EXPENDIP",training_frame = hdata,hidden = c(25,25),epochs = 100,
                      train_samples_per_iteration = -1,validation_frame = hdata,activation = "Tanh",distribution = "tweedie", score_training_samples=0)
	pr = as.data.frame(h2o.predict(hh,newdata = hdata))
	pr = log(pr)
	dPower=1.5
	mean_deviance = (sum((MEPS$EXPENDIP^(2.0-dPower)/((1.0-dPower)*(2.0-dPower)) -
   		MEPS$EXPENDIP*exp(pr[,1]*(1.0-dPower))/(1.0-dPower) + exp(pr[,1]*(2.0-dPower))/(2.0-dPower) ))*2) /157  ## tweedie deviance

  print("---------------------------------------------------------tweedie-----------------------------------------------")
        print(mean_deviance)
        print(hh@model$training_metrics@metrics$mean_residual_deviance)
        print(hh@model$validation_metrics@metrics$mean_residual_deviance)
  print("---------------------------------------------------------tweedie-----------------------------------------------")


	expect_equal(mean_deviance,hh@model$training_metrics@metrics$mean_residual_deviance, 1e-8)
	expect_equal(mean_deviance,hh@model$validation_metrics@metrics$mean_residual_deviance, 1e-8)
	

	print("poisson")
	fre = h2o.uploadFile(locate("smalldata/glm_test/freMTPL2freq.csv.zip"),destination_frame = "fre")
	fre$VehPower = as.factor(fre$VehPower)
	hh = h2o.deeplearning(x = 4:12,y = "ClaimNb",training_frame = fre,hidden = c(5,5),epochs = 1,
                       train_samples_per_iteration = -1,validation_frame = fre,activation = "Tanh",distribution = "poisson", score_training_samples=0)
        p = h2o.predict(hh,newdata = fre)[,1]
        nr <- nrow(p)
	# ClaimNb <- as.data.frame(fre)['ClaimNb']
	# min value of logarithm due to hex.LogExpUtil.MIN_LOG
	min_log <- -19.0
	div <- fre$ClaimNb[,1]/p[,1]
	div <- pmax(exp(-19.0), as.vector(div[,1]))
	print(p[,1])
	ClaimNbPMin <- pmax(min_log, log(div))
	# pMin <- pmax(min_log, p[,1])
	print(length(fre$ClaimNb[,1]))
	print(p)
	# deviance <- 2 * sum(fre$ClaimNb[,1] * (log(ClaimNbMin) - log(p[,1])) - fre$ClaimNb[,1] + p[,1])
	# deviance <- 2 * sum(fre$ClaimNb[,1] * log(ClaimNbPMin) - fre$ClaimNb[,1] + p[,1])
	deviance <- 2 * sum(fre$ClaimNb[,1] * ClaimNbPMin - fre$ClaimNb[,1] + p[,1])
	print(deviance)

	# for (i in 1: nr) {
	# 		print('---')
	# 	print(fre$ClaimNb[i,1])
	# 	print(p[i,1])
	# 	print(2 * sum(fre$ClaimNb[i,1] * ClaimNbPMin[i] - fre$ClaimNb[i,1] + p[i,1]))
	# 	}
	mean_deviance <- deviance/nr ## Poisson deviance
	print(mean_deviance)
  #
  print("---------------------------------------------------------poisson-----------------------------------------------")
        print(mean_deviance)
        print(hh@model$training_metrics@metrics$mean_residual_deviance)
        print(hh@model$validation_metrics@metrics$mean_residual_deviance)
  print("---------------------------------------------------------poisson-----------------------------------------------")

	expect_equal(mean_deviance, hh@model$training_metrics@metrics$mean_residual_deviance, tolerance=1e-8)
	expect_equal(mean_deviance, hh@model$validation_metrics@metrics$mean_residual_deviance, tolerance=1e-8)
	
	
}
doTest("DL residual deviance Test: DL deviance for poisson/gamma/tweedie distributions", test)
