setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test <- function(conn) {
  foo <- as.h2o(iris)
  scale(iris[,1:4],center=c(4,3,2,1), scale=c(1,1,1,1))
  scale(iris[,1:4],center=c(4,3,2,1), scale=T)
  scale(iris[,1:4],center=T, scale=c(1,1,1,1))
  scale(iris[,1:4],center=c(4,3,2,1), scale=F)
  scale(iris[,1:4],center=F, scale=c(1,1,1,1))

  testEnd()
}

doTest("testing scale", test)