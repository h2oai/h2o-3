setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test out the dim() functionality
# If data frame, get back a vector of two numbers: [1] nrows ncols
# If NAs in the frame, they still count.
# If not a frame, expect NULL
##






test.dim <- function() {
  h2oTest.logInfo("Uploading logreg/princeton/cuse.dat")
  hex <- h2o.importFile(h2oTest.locate("smalldata/logreg/prostate.csv"), "pros.hex")
  
  Rdat <- read.csv(h2oTest.locate("smalldata/logreg/prostate.csv"))
  
  h2oTest.logInfo("The dimension of the data when read into R is: ")
  print(dim(Rdat))
  
  h2oTest.logInfo("The dimension of the data when asking h2o: ")
  print(dim(hex))
  
  expect_that(dim(Rdat), equals(dim(hex)))

  h2oTest.logInfo("Slice out a column and data frame it, try dim on it...")
  
  h2oCol <- hex[,5]
  RCol <- Rdat[,5]
 
  h2oTest.logInfo("dim(h2oCol)") 
  print(dim(h2oCol))

  RCol <- data.frame(RCol)
  
  h2oTest.logInfo("dim(RCol)")
  print(dim(RCol))

  expect_that(dim(RCol), equals(dim(h2oCol)))
 
  h2oTest.logInfo("OK, now try an operator, e.g. '&', and then check dimensions agao...")
  
  RColAmpFive <- RCol & 5
  h2oColAmpFive <- h2oCol & 5
  
  expect_that(dim(RColAmpFive), equals(dim(h2oColAmpFive)))
  
  h2oTest.logInfo("Final check: data frame h2oCol then perform operation, should be the same as others...")
  h2oTest.logInfo("data.frame(as.data.frame(h2oCol))")
  print(data.frame(as.data.frame(h2oCol)))
  h2oTOR <- data.frame(as.data.frame(h2oCol) & 5)
  h2oTest.logInfo("as.data.frame(h2oCol) & 5")
  expect_that(dim(h2oTOR), equals(dim(h2oColAmpFive)))
  
}



h2oTest.doTest("Test out the dim() functionality", test.dim)
