setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub.859 <- function() {

  a_initial = as.data.frame(cbind( 
    c(0,0,1,0,0,1,0,0,0,0), 
    c(1,1,1,0,1,0,1,0,1,0), 
    c(1,0,1,0,1,0,1,0,0,1), 
    c(1,1,0,0,0,1,0,0,0,1), 
    c(1,1,1,0,1,0,0,0,1,1), 
    c(1,0,1,0,0,0,0,0,1,1), 
    c(1,1,1,0,0,0,1,1,1,0), 
    c(0,0,1,1,1,0,0,1,1,0), 
    c(0,1,1,1,1,0,0,1,1,0), 
    c(0,0,0,0,0,1,1,0,0,0) 
  )) 
  a = a_initial 
  a.h2o <- as.h2o(a_initial, destination_frame="r.hex") 
  d = ifelse(F, a.h2o[1,] , apply(a.h2o, 2, sum)) 
  dd = ifelse(F, a[1,] , apply(a, 2, sum))
  d.h2o = as.data.frame(d)
  dd
  d.h2o
  expect_that(all(d.h2o == dd), equals(T)) 
  
}

h2oTest.doTest("Test pub 859", test.pub.859)
