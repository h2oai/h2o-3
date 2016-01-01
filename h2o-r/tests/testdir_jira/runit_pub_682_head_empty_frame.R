setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.head_empty_frame <- function() {

  a_initial = cbind(c(0,0,0,0), c(1,1,1,1)) 
  # create data frame without col names in R, transfer to h2o (V* names preserved from R) 
  a.h2o <- as.h2o(a_initial, destination_frame="A.hex")
  
  # now we'll create an empty b.h2o in h2o 
  b.h2o = a.h2o[a.h2o$V1==32,]
  
  head(b.h2o[,1]) 
  head(b.h2o[1,]) 
  
  
}

h2oTest.doTest("Test frame add.", test.head_empty_frame)
