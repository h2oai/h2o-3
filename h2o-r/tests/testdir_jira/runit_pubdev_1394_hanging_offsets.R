setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################
## Offsets are hanging
######################################




test.hanging.offset <- function() {
  pros.hex <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])

  myglm <- h2o.glm(x = 3:9, y = 2, training_frame = pros.hex, family = "binomial", offset = "PSA")

  
}

h2oTest.doTest("Testing GLM Offsets that Hang", test.hanging.offset)
