setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
# This tests p-values in glm by comparing results in R
# dataset - synthetic dataset

test.pvalue.syn <- function(conn){
	set.seed(123)
	N=7000; p=200
	nzc=7
	x=matrix(rnorm(N*p),N,p)
	beta=rnorm(nzc)
	f = x[,seq(nzc)]%*%beta
	mu=exp(f)
	y=rpois(N,mu)
	wts = sample(1:6, N, TRUE)*10

	family = c("gaussian","poisson","tweedie","gamma","binomial")

	for(i in 1:4){
 	 if(i ==4){
    	set.seed(12)
    	y = (rgamma(N,shape = 6.4))
    	data =cbind(y,wts,x)
    	data = data.frame(data)
    	hdata = as.h2o(data,destination_frame = "data")
    	distribu = family[i] 
    	print(distribu)
    	(gg1 =glm(y~.- wts,family = "Gamma",data = data))
    	r_pval = as.numeric(summary(gg1)$coefficients[,4])
    	hh1 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,
                  family =distribu,standardize = F)
    	h_pval = hh1@model$coefficients_table[,5]
    	expect_equal(r_pval,h_pval,tolerance = 1e-4)
    
    	sdata =data.frame(scale(data))
    	sdata$y = data$y
    	hsdata = as.h2o(sdata,destination_frame = "sdata")
    	gg1 =glm(y~.- wts,family = "Gamma",data = sdata)
    	r_pval = as.numeric(summary(gg1)$coefficients[,4])
    	hh1 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hsdata)),y = 1,training_frame = hsdata,lambda = 0,compute_p_values = T,
                  family =distribu,standardize = T)
    	h_pval = hh1@model$coefficients_table[,5]
    	expect_equal(r_pval,h_pval,tolerance = 1e-4)
    
    	(gg2 =glm(y~.- wts,family = "Gamma",data = data,weights = wts/3))
    	r_pval = as.numeric(summary(gg2)$coefficients[,4])
    	hdata$wts = hdata$wts/3
    	hh2 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,weights_column = "wts",
                  family =distribu,standardize = F)
   		h_pval = hh2@model$coefficients_table[,5]
    	expect_equal(r_pval,h_pval,tolerance = 1e-4)
    
    	(gg3 =glm(y~.- wts,family = "Gamma",data = data,offset = rep(.1,length(data$wts))))
    	r_pval = as.numeric(summary(gg3)$coefficients[,4])
    	offset = as.h2o(rep(.1,length(data$wts)),destination_frame = "offset")
    	hdata = h2o.cbind(hdata,offset) 
    	hh3 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:(length(colnames(hdata))-1),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,offset_column = "x",
                  family =distribu,standardize = F)
    	h_pval = hh3@model$coefficients_table[,5]
    	expect_equal(r_pval,h_pval,tolerance = 1e-4)
    
  	 }else{
  
    	data =cbind(y,wts,x)
    	data = data.frame(data)
    	hdata = as.h2o(data,destination_frame = "data")
    	distribu = family[i] 
    	print(distribu)
    	print("non-standardized")
    	gg1 =glm(y~.- wts,family = distribu,data = data)
    	r_pval = as.numeric(summary(gg1)$coefficients[,4])
    	hh1 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,
                  family =distribu,standardize = F)
    	h_pval = hh1@model$coefficients_table[,5]
    	expect_equal(r_pval,h_pval,tolerance = 1e-4)
    
    	print("standardized")
    	sdata =data.frame(scale(data))
    	sdata$y = data$y
    	hsdata = as.h2o(sdata,destination_frame = "sdata")
    	gg1 =glm(y~.- wts,family = distribu,data = sdata)
    	r_pval = as.numeric(summary(gg1)$coefficients[,4])
    	hh1 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hsdata)),y = 1,training_frame = hsdata,lambda = 0,compute_p_values = T,
                  family =distribu,standardize = T)
    	h_pval = hh1@model$coefficients_table[,5]
    	expect_equal(r_pval,h_pval,tolerance = 1e-4)
    	print("II")
    	hh1 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,
                  family =distribu,standardize = T)
    	h_pval = hh1@model$coefficients_table[,5]
    	expect_equal(r_pval,h_pval,tolerance = 1e-4)
    	#expect_equal(as.numeric(hh1@model$coefficients_table[,2]),as.numeric(hh1@model$coefficients_table[,6]),tolerance = 1e-4)
    
    	print("weight")
    	(gg2 =glm(y~.- wts,family = distribu,data = data,weights = wts))
    	r_pval = as.numeric(summary(gg2)$coefficients[,4])
    	hh2 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,weights_column = "wts",
                  family =distribu,standardize = F)
    	h_pval = hh2@model$coefficients_table[,5]
    	expect_equal(r_pval,h_pval,tolerance = 1e-4)
    
    	print("offset")
    	(gg3 =glm(y~.- wts,family = distribu,data = data,offset = wts/10))
    	r_pval = as.numeric(summary(gg3)$coefficients[,4])
    	hdata$wts = hdata$wts/10
    	hh3 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,offset_column = "wts",
                  family =distribu,standardize = F)
    	h_pval = hh3@model$coefficients_table[,5]
    	expect_equal(r_pval,h_pval,tolerance = 1e-4)
  	}
    
   }

	distribu = family[5] 
	y = rbinom(N,size = 1,prob = .02)
	data =cbind(y,wts,x)
	data = data.frame(data)
	hdata = as.h2o(data,destination_frame = "data")

	print(distribu)
	print("non-standardized")
	gg1 =glm(y~.- wts,family = distribu,data = data)
	r_pval = as.numeric(summary(gg1)$coefficients[,4])
	hh1 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,
              family =distribu,standardize = F)
	h_pval = hh1@model$coefficients_table[,5]
	expect_equal(r_pval,h_pval,tolerance = 1e-4)

	print("standardized")
	sdata =data.frame(scale(data))
	sdata$y = data$y
	hsdata = as.h2o(sdata,destination_frame = "sdata")
	gg1 =glm(y~.- wts,family = distribu,data = sdata)
	r_pval = as.numeric(summary(gg1)$coefficients[,4])
	hh1 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hsdata)),y = 1,training_frame = hsdata,lambda = 0,compute_p_values = T,
	            family =distribu,standardize = T)
	h_pval = hh1@model$coefficients_table[,5]
	expect_equal(r_pval,h_pval,tolerance = 1e-4)
	print("II")
	hh1 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,
              family =distribu,standardize = T)
	h_pval = hh1@model$coefficients_table[,5]
	expect_equal(r_pval,h_pval,tolerance = 1e-4)
	#expect_equal(as.numeric(hh1@model$coefficients_table[,2]),as.numeric(hh1@model$coefficients_table[,6]),tolerance = 1e-4)

	print("weight")
	(gg2 =glm(y~.- wts,family = distribu,data = data,weights = wts))
	r_pval = as.numeric(summary(gg2)$coefficients[,4])
	hh2 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,weights_column = "wts",
              family =distribu,standardize = F)
	h_pval = hh2@model$coefficients_table[,5]
	expect_equal(r_pval,h_pval,tolerance = 1e-4)

	print("offset")
	(gg3 =glm(y~.- wts,family = distribu,data = data,offset = wts/10))
	r_pval = as.numeric(summary(gg3)$coefficients[,4])
	hdata$wts = hdata$wts/10
	hh3 = h2o.glm(objective_epsilon=0, beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,offset_column = "wts",
              family =distribu,standardize = F)
	h_pval = hh3@model$coefficients_table[,5]
	expect_equal(r_pval,h_pval,tolerance = 1e-4)
	
	
 }
h2oTest.doTest("Test p-vlaues on synthetic data", test.pvalue.syn)
