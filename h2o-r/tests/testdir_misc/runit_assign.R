setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test assigned NAs
##

test <- function() {
	iris <- as.h2o(iris)
	numNAs <- 40
  s <- sample(nrow(iris),numNAs)
  iris[s,5] <- NA
  print(summary(iris))
  expect_that(sum(is.na(iris[5])), equals(numNAs))
  
}

h2oTest.doTest("Count assigned NAs", test)

