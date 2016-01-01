setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")




## ---------------------------------------------------- ##
# GLM fails with LBFGS, alpha > 0, and lambda_search = T #
## ---------------------------------------------------- ##

test.pubdev1042 <- function(){
  pros.hex <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv.zip"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")

  h2o.glm(x = 3:9, y = 2, training_frame = pros.train, family = "binomial", solver = "L_BFGS",
    max_iterations = 25, alpha = 0.5, lambda_search = TRUE)

  
}

h2oTest.doTest("Testing LBFGS with alpha and lambda search", test.pubdev1042)
