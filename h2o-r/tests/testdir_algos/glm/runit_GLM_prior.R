setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests priors in glm on a synthetic dataset ######




test <- function() {
	print("Create sythetic data with intercept = -3.35 and coefficent = 2 for binomial classification with class distribution 0.1")
	set.seed(12)
	x=rnorm(98435);
	p=1/(1+exp(-(-3.35+2*x)));
	y=rbinom(98435,1,p);
	summary(as.factor(y))
	full = data.frame(y,x)

	print("Create sub frame with class distribution 0.5")
	o_sub = full[which(full$y==1),]
	zz = full[which(full$y==0),]
	z_sub = zz[sample(1:nrow(zz),size = round(nrow(zz)*(1/9)),replace = F),]
	sub = rbind(o_sub,z_sub)
	dim(sub)
	
	print("Calculate prior, offset and weights")
	prior = length(which(full$y==1))/nrow(full) # probabilty of an event
	r1 = length(which(sub$y==1))/nrow(sub)      # proportion of events in the dataset

	sub$offset=log( (r1*(1-prior)) / ((1-r1)*prior) );
	w1=prior/r1; 
	w2=(1-prior)/(1-r1);
	sub$weight = ifelse(sub$y==1,yes = w1,no = w2)

	print("Parse data into H2O")
	sub_frame = as.h2o(sub,destination_frame = "sub")
	full_frame = as.h2o(full,destination_frame = "full")

	### Expect b0 = 3.35(approx) and b1 = 2(approx) when run with appropriate prior, offset and weights ###
	print("Build Models")
	glm_with_prior = h2o.glm(x = 2,y = 1,training_frame = sub_frame,family = "binomial",prior = prior)
	print(glm_with_prior@model$coefficients_table)
	
	glm_with_offset = h2o.glm(x = 2,y = 1,training_frame = sub_frame,family = "binomial",offset_column = "offset")
	print(glm_with_offset@model$coefficients_table)
	
	glm_with_weight = h2o.glm(x = 2,y = 1,training_frame = sub_frame,family = "binomial",prior = prior,weights_column = "weight")
	print(glm_with_weight@model$coefficients_table)
	
	glm_with_full = h2o.glm(x = 2,y = 1,training_frame = full_frame,family = "binomial")
	print(glm_with_full@model$coefficients_table)

	expect_equal(glm_with_prior@model$coefficients[1],glm_with_offset@model$coefficients[1],tolerance=1e-4)
	expect_equal(glm_with_prior@model$coefficients[2],glm_with_offset@model$coefficients[2],tolerance=1e-4)

	print(paste("Auc from prior adjusted model: ",  h2o.auc(glm_with_prior),sep = ''))
	print(paste("Auc from offset adjusted model: ", h2o.auc(glm_with_offset),sep = ''))
	print(paste("Auc from weight adjusted model: ", h2o.auc(glm_with_weight),sep = ''))
	print(paste("Auc from full dataset: ",          h2o.auc(glm_with_full),sep = ''))


	############### Expect: only intercept(b0) to change when change prior ############
	print("Build Models with different priors and check  coefficients")
	glm_with_prior1 = h2o.glm(x = 2,y = 1,training_frame = sub_frame,family = "binomial",prior = .1)
	print(glm_with_prior1@model$coefficients_table)

	glm_with_prior2 = h2o.glm(x = 2,y = 1,training_frame = sub_frame,family = "binomial",prior = .9)
	print(glm_with_prior2@model$coefficients_table)

	expect_equal(glm_with_prior1@model$coefficients[2],glm_with_prior2@model$coefficients[2])
	
}


h2oTest.doTest("GLM prior Test: GLM w/ prior offset and weights", test)
