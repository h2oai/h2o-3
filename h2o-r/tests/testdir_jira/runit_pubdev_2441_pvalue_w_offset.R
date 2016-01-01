setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# This tests p-values with offset in glm by comparing results in R
# dataset - synthetic dataset

test.pvalue.syn <- function(conn){
	set.seed = 123
	N=10; p=2
	nzc=2
	x=matrix(rnorm(N*p),N,p)
	beta=rnorm(nzc)
	f = x[,seq(nzc)]%*%beta
	mu=exp(f)
	y=rpois(N,mu)
	wts = sample(1:6, N, TRUE)*10

	data =cbind(y,wts,x)
	data = data.frame(data)
	hdata = as.h2o(data,destination_frame = "data")
	#For gaussian
	(gg3 =glm(y~.- wts,family = "gaussian",data = data,offset = wts))
	r_pval = as.numeric(summary(gg3)$coefficients[,4])
	hh3 = h2o.glm(objective_epsilon=0,beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,offset_column = "wts",
              family ="gaussian",standardize = F)
	h_pval = hh3@model$coefficients_table[,5]
	expect_equal(r_pval,h_pval,tolerance = 1e-4)
	hh3
	summary(gg3)
	diff = abs(h_pval - r_pval)
	checkTrue(max(diff) < 1e-4, "p-values do not match")
	#For tweedie
	(gg3 =glm(y~.- wts,family = tweedie(var.power=1,link.power=0),data = data,offset = wts/10))
	r_pval = as.numeric(summary(gg3)$coefficients[,4])
	hdata$wts = hdata$wts/10
	hh3 = h2o.glm(objective_epsilon=0,beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = T,offset_column = "wts",
              tweedie_variance_power = 1,tweedie_link_power = 0,family ="tweedie",standardize = F)
	h_pval = hh3@model$coefficients_table[,5]
	diff = abs(h_pval - r_pval)
	checkTrue(max(diff) < 1e-4, "p-values do not match")
}
h2oTest.doTest("Test p-values with offset on synthetic data", test.pvalue.syn)
