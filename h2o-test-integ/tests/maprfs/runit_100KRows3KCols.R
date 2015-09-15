#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit-hadoop.R')

ipPort <- get_args(commandArgs(trailingOnly = TRUE))
myIP   <- ipPort[[1]]
myPort <- ipPort[[2]]

library(RCurl)
library(h2o)
library(testthat)

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

heading("BEGIN TEST")
h2o.init(ip=myIP, port=myPort, startH2O = FALSE)

data.hex <- h2o.importFile("maprfs:/datasets/WU_100KRows3KCols.csv")

#print(summary(data.hex))

myY = "C1"
myX = setdiff(names(data.hex), myY) 

# GLM Model
data.glm <- h2o.glm(myX, myY, training_frame = data.hex, family = 'gaussian', solver = 'L_BFGS')
print(data.glm)

# GBM Model
data.gbm <- h2o.gbm(myX, myY, training_frame = data.hex, distribution = 'gaussian')
print(data.gbm)

PASS_BANNER()
