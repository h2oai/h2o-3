setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# This tests p-values with offset in glm by comparing results in R for Tweedie
# dataset - synthetic dataset

test.pvalue.syn.tweedie <- function(conn){
	hdata <- h2o.importFile(locate("smalldata/glm_test/tweediePvalue.csv"))
	data <- as.data.frame(hdata)
	wts <- data$wts
	#For tweedie
	(gg3 =glm(y~.- wts,family = tweedie(var.power=1,link.power=0),data = data,offset = wts/10))
	r_pval = as.numeric(summary(gg3)$coefficients[,4])
	hdata$wts = hdata$wts/10
	hh3 = h2o.glm(objective_epsilon=0,beta_epsilon=1e-8,x = 3:length(colnames(hdata)),y = 1,training_frame = hdata,lambda = 0,compute_p_values = TRUE,offset_column = "wts",
              tweedie_variance_power = 1,tweedie_link_power = 0,family ="tweedie",standardize = FALSE)
	h_pval = hh3@model$coefficients_table[,5]
	hh3
	summary(gg3)
	diff = abs(h_pval - r_pval)
	print("For Tweedie: h2o p-val: ")
	print(h_pval)
	print("For Tweedie: R p-val: ")
	print(r_pval)
	print("p vaule differences: ")
	print(diff)
	expect_equal(r_pval,h_pval,tolerance = 2e-4)
}
doTest("Test p-values with offset on synthetic data for Tweedie", test.pvalue.syn.tweedie)
