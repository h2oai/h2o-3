setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#This tests quantile and weighted quantile on synthetic data by comparing with R

test.quantile <- function(conn){
N = 1000

x = rgamma(N, shape=0.067,  scale = 0.008) 
aa = as.h2o(x)
r_q = quantile(x,  probs = c(0.1, 0.5, 1, 2, 5, 10, 50,88.83,99,90)/100,na.rm=T)
h_q = h2o.quantile(aa,probs = c(0.1, 0.5, 1, 2, 5, 10, 50,88.83,99,90 )/100,na.rm=T)
expect_equal(r_q,h_q )

x = rlnorm(N,meanlog = 12,sdlog = 132)
aa = as.h2o(x)
r_q = quantile(x,  probs = seq(0,1,.05),na.rm=T)
h_q = h2o.quantile(aa,probs = seq(0,1,.05),na.rm=T)
expect_equal(r_q,h_q )

x = rexp(N, rate = 12.3) 
ss = sample(1:N,size = N/10,replace = F)
 x[ss]=NA
aa = as.h2o(x)
r_q = quantile(x,  probs = seq(0,1,.05),na.rm=T)
h_q = h2o.quantile(aa,probs = seq(0,1,.05),na.rm=T)
expect_equal(r_q,h_q )

#weighted quantiles
#library(Hmisc)
set.seed(1)
N=1e5
x = runif(N)
aa = as.h2o(x)
wts = sample(1:6, N, TRUE)
aa$h_wts = as.h2o(wts)
#r_q = wtd.quantile(x, wts, probs = seq(0,1,.05))
r_q=c(3.895489e-06,4.863379e-02,9.789691e-02,1.470487e-01,1.977443e-01,2.473365e-01,2.975013e-01,3.482667e-01,3.980460e-01,4.483631e-01,4.990024e-01,5.489128e-01,5.986945e-01,6.486255e-01,6.991498e-01,7.500031e-01,8.001472e-01,8.504057e-01,8.996923e-01,9.498159e-01,9.999471e-01)
h_q = h2o.quantile(aa,probs = seq(0,1,.05),weights_column = "h_wts")
expect_true(max(abs((r_q-h_q)/r_q)) < 1e-5)

}
h2oTest.doTest("Test quantile",test.quantile )
