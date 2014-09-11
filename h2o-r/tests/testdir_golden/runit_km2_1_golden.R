setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.km2vanilla.golden <- function(H2Oserver) {
#Import data: 
Log.info("Importing IRIS data...") 
irisH2O<- h2o.uploadFile(H2Oserver, locate("../../smalldata/iris/iris.csv"), key="irisH2O")
irisR<- read.csv(locate("smalldata/iris/iris.csv"), header=F)


fitR<- kmeans(irisR[,1:4], centers=3, iter.max=1000, nstart=10)
fitH2O<- h2o.kmeans(irisH2O, centers=3, cols=c("C1", "C2", "C3", "C4"))


wssR<-sort.int(fitR$withinss)
wssH2O<- sort.int(fitH2O@model$withinss)

Log.info(paste("H2O WithinSS  : ", wssH2O, "\t\t", "R WithinSS : ", wssR))

Log.info("Compare Within SS between R and H2O")
expect_equal(wssR, wssH2O, tolerance = 0.10)

testEnd()
}

doTest("KMeans Test: Golden Kmeans2 - Vanilla Iris Clustering", test.km2vanilla.golden)
