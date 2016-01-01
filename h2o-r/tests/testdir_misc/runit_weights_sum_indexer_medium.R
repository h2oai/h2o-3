setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test <- function() {
  sampleSize <- 75553
  hex <- h2o.importFile(h2oTest.locate("bigdata/laptop/covtype/covtype.data"), "hex")
  hex[,"weights"] <- 0
  indexes <- sample(nrow(hex), sampleSize)
  hex[indexes, "weights"] <- 1
  weightsSum <- sum(hex[,"weights"])
  print(weightsSum)
  print(sampleSize)
  expect_true(round(weightsSum) == sampleSize)
}

h2oTest.doTest("sum of weights should be == sampleSize", test)
