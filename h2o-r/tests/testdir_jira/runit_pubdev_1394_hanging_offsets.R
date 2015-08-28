######################################
## Offsets are hanging
######################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.hanging.offset <- function(conn) {
  pros.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])

  myglm <- h2o.glm(x = 3:9, y = 2, training_frame = pros.hex, family = "binomial", offset = "PSA")

  testEnd()
}

doTest("Testing GLM Offsets that Hang", test.hanging.offset)
