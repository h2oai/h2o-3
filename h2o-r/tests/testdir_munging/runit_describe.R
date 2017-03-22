setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.h2o.describe <- function() {
  iris.hex <- as.h2o(iris)
  describe <- h2o.describe(iris.hex)
  
  mins = as.matrix(apply(iris.hex, 2, min))
  maxs = as.matrix(apply(iris.hex, 2, max))
  means = as.matrix(apply(iris.hex, 2, mean))
  # sd in apply currently broken
  # sds = as.matrix(apply(iris.hex, 2, h2o.sd))
  sds = sapply(names(iris.hex), function(i) h2o.sd(iris.hex[,i]))
  lvls = sapply(names(iris.hex), function(i) length( h2o.levels(iris.hex[,i])))
  lvls[lvls == 0] = NA
  
  checkEqualsNumeric(describe$Min, mins)
  checkEqualsNumeric(describe$Max, maxs)
  checkEqualsNumeric(describe$Mean, means)
  checkEqualsNumeric(describe$Sigma, sds)
  checkEqualsNumeric(describe$Cardinality, lvls)
}

doTest("Test h2o.describe", test.h2o.describe)
