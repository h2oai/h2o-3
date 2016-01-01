setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# Test to make sure that you do not lose a RTMP frame in H2O when you execute an erroneous line of R code
# R behavior: Reports an error but keeps the frame as is

test.pubdev.2386 <- function(conn){

  # Import Iris dataset
  iris.hex <- as.h2o(iris)

  # Create a frame to join with iris.hex 
  ci <- as.h2o(data.frame(Species = c("setosa", "setosa", "virginica"), ClusterID = c(1,2,3) ) )
  
  # Try to merge the ci frame with the iris frame, which should throw an error that will be ignored
  try(expr = iris.hex <- h2o.merge(x = iris.hex, y = ci) , silent = T)
  
  # Check to see if iris.hex still exists in the DKV
  if(!all(dim(iris.hex) == c(150,5))) stop("H2OFrame is no longer there!")
  
}
h2oTest.doTest("Test for Missing RTMPs PUBDEV-2386", test.pubdev.2386)
