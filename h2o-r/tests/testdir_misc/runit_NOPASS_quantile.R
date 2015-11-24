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
x = runif(N)
aa = as.h2o(x)
wts = sample(1:6, N, TRUE)
h_wts = as.h2o(wts)
#r_q = wtd.quantile(x, wts, probs = seq(0,1,.05))
#h_q = h2o.quantile(aa,probs = seq(0,1,.05),weight_column = h_wts)
#expect_equal(r_q,h_q )

}
doTest("Test quantile",test.quantile )