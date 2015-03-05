#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))

heading <- function(message) {
    cat("\n")
    cat(message)
    cat("\n")
    cat("\n")
}

PASS_BANNER <- function() {
    cat("\n")
    cat("PASS\n")
    cat("\n")
}

get_args <- function(args) {
  fileName <- commandArgs()[grep('*\\.R',unlist(commandArgs()))]
  if (length(args) > 1) {
    m <- paste("Usage: R f ", paste(fileName, " --args H2OServer:Port",sep=""),sep="")
    stop(m);
  }

  if (length(args) == 0) {
    myIP   = "127.0.0.1"
    myPort = 54321
  } else {
    argsplit = strsplit(args[1], ":")[[1]]
    myIP     = argsplit[1]
    myPort   = as.numeric(argsplit[2])
  }
  return(list(myIP,myPort));
}

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

iris.hex <- h2o.importFile(conn, "maprfs://mr-0xf1/datasets/iris.csv")
print(summary(iris.hex))

myX = 1:4
myY = 5

# GBM Model
iris.gbm <- h2o.gbm(myX, myY, training_frame = iris.hex)
print(iris.gbm)

# GLM Model 
iris.glm <- h2o.glm(myX, myY, training_frame = iris.hex, family = "gaussian")
print(iris.glm)

# DL Model
iris.dl  <- h2o.deeplearning(myX, myY, training_frame = iris.hex, epochs=1, hidden=c(50,50))
print(iris.dl)

PASS_BANNER()
