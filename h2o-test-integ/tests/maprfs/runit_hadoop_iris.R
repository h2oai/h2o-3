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

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Check if we are running inside the 0xdata network by seeing if we can touch
# the HDP2.1 namenode. Update if using other clusters.
# Note this should fail on home networks, since 176 is not likely to exist
# also should fail in ec2.

#----------------------------------------------------------------------

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort, startH2O = FALSE)

iris.hex <- h2o.importFile(conn, "maprfs:/datasets/iris/iris.csv")

print(summary(iris.hex))

myX = 1:4
myY = 5

# GBM Model
iris.gbm <- h2o.gbm(myX, myY, training_frame = iris.hex, distribution = 'multinomial')
print(iris.gbm)

myZ = 1

# GLM Model
iris.glm <- h2o.glm(myX, myZ, training_frame = iris.hex, family = "gaussian")
print(iris.glm)


# DL Model
iris.dl  <- h2o.deeplearning(myX, myY, training_frame = iris.hex, epochs=1, hidden=c(50,50), loss = 'CrossEntropy')
print(iris.dl)

# DRF Model
# iris.drf <- h2o.randomforest(myX, myY, training_frame = iris.hex)

PASS_BANNER()
