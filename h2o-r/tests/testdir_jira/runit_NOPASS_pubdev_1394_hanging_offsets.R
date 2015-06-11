######################################
## Offsets are hanging
######################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.hanging.offset <- function(conn) {
  pros.hex <- h2o.uploadFile(conn, locate("smalldata/prostate/prostate.csv"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")


  myglm <- h2o.glm(x = 3:9, y = 2, training_frame = pros.train, family = "binomial", offset = "PSA")

  testEnd()
}