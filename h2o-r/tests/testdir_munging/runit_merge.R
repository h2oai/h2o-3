setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test out the merge() functionality
##


test.merge <- function() {

  #HEXDEV-536
  g <- data.frame(A=c(1,1,2,2,3,3),B=rep(8,6))
  h <- data.frame(A=c(1))
  run.merge.tests(g,h)
  h <- data.frame(A=c(2:4))
  run.merge.tests(g,h)

  #HEXDEV-538
  g <- data.frame(A = c(1,1,2,2,3,3), B = c(0.1,0.3,0.6,1,1.5,2.1), index=1:6)
  h <- data.frame(A = 1:3,mean_B = c(.2,.8,1.8), sdev_B = c(sd(c(.1,.3)), sd(c(.6,1)),sd(c(1.5,2.1))))
  run.merge.tests(g,h)  
}

run.merge.tests <- function (g,h) {
  h2o_g <- as.h2o(g)
  h2o_h <- as.h2o(h)
  h2o_merge <- h2o.merge(h2o_g, h2o_h)
  R_merge <- merge(g,h)
  h2o_and_R_equal(h2o_merge, R_merge[names(h2o_merge)])
}


doTest("Test out the merge() functionality", test.merge)
