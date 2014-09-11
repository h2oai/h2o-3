setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.quantiles.golden <- function(H2Oserver) {

#Import data: (the data are 20000 observations pulled from known distributions - parameters given at end of test)
Log.info("Importing MAKE and RUNIF data...") 
makeH2O<- h2o.uploadFile(H2Oserver, locate("../../smalldata/makedata.csv"), key="makeH2O")
makeR<- read.csv(locate("smalldata/makedata.csv"), header=T)
runifH2O<- h2o.uploadFile(H2Oserver, locate("../../smalldata/runif.csv"), key="runifH2O")
runifR<- read.csv(locate("smalldata/runif.csv"), header=T)

#generate quantiles from R
qrR2<- as.data.frame(quantile(runifR[,2]))
qrR3<- as.data.frame(quantile(runifR[,3]))
qrR4<- as.data.frame(quantile(runifR[,4]))
makeR2<- as.data.frame(quantile(makeR[,2]))
makeR3<- as.data.frame(quantile(makeR[,3]))
makeR4<- as.data.frame(quantile(makeR[,4]))
makeR5<- as.data.frame(quantile(makeR[,5]))
makeR6<- as.data.frame(quantile(makeR[,6]))
makeR7<- as.data.frame(quantile(makeR[,7]))
makeR8<- as.data.frame(quantile(makeR[,8]))
makeR9<- as.data.frame(quantile(makeR[,9]))

#generate quantiles from H2O
qrH2<- as.data.frame(quantile(runifH2O[,2]))
qrH3<- as.data.frame(quantile(runifH2O[,3]))
qrH4<- as.data.frame(quantile(runifH2O[,4]))
makeH2<- as.data.frame(quantile(makeH2O[,2]))
makeH3<- as.data.frame(quantile(makeH2O[,3]))
makeH4<- as.data.frame(quantile(makeH2O[,4]))
makeH5<- as.data.frame(quantile(makeH2O[,5]))
makeH6<- as.data.frame(quantile(makeH2O[,6]))
makeH7<- as.data.frame(quantile(makeH2O[,7]))
makeH8<- as.data.frame(quantile(makeH2O[,8]))
makeH9<- as.data.frame(quantile(makeH2O[,9]))


#Print Quantiles for Both: (note that makeH2 corresponds to the H2O quantiles for the second col of make dataframe, where makeR2 is the #quantiles produced by R for the second column of make data frame) 

Log.info("Print summary for H2O and R... \n")

Log.info(paste("H2O Make 2  : ", makeH2))      
Log.info(paste("R Make 2  : ", makeR2))
Log.info(paste("H2O Make 3  : ", makeH3))      
Log.info(paste("R Make 3  : ", makeR3))
Log.info(paste("H2O Make 4  : ", makeH4))      
Log.info(paste("R Make 4  : ", makeR4))
Log.info(paste("H2O Make 5  : ", makeH5))      
Log.info(paste("R Make 5  : ", makeR5))
Log.info(paste("H2O Make 6  : ", makeH6))      
Log.info(paste("R Make 6  : ", makeR6))
Log.info(paste("H2O Make 7  : ", makeH7))      
Log.info(paste("R Make 7  : ", makeR7))
Log.info(paste("H2O Make 8  : ", makeH8))      
Log.info(paste("R Make 8  : ", makeR8))
Log.info(paste("H2O Make 9  : ", makeH9))      
Log.info(paste("R Make 9  : ", makeR9))
Log.info(paste("H2O Runif 2  : ", qrH2))      
Log.info(paste("R Runif 2  : ", qrR2))
Log.info(paste("H2O Runif 3  : ", qrH3))      
Log.info(paste("R Runif 3  : ", qrR3))
Log.info(paste("H2O Runif 4  : ", qrH4))      
Log.info(paste("R Runif 4  : ", qrR4))


Log.info("Compare H2O quantiles to R quantiles... \n")
expect_equal(makeH2[,1], makeR2[,1], tolerance=.03)
expect_equal(makeH3[,1], makeR3[,1], tolerance=.03)
expect_equal(makeH4[,1], makeR4[,1], tolerance=.03)
expect_equal(makeH5[,1], makeR5[,1], tolerance=.03)
expect_equal(makeH6[,1], makeR6[,1], tolerance=.03)
expect_equal(makeH7[,1], makeR7[,1], tolerance=.03)
expect_equal(makeH8[,1], makeR8[,1], tolerance=.03)
expect_equal(makeH9[,1], makeR9[,1], tolerance=.03)
expect_equal(qrH2[,1], qrR2[,1], tolerance=.03)
expect_equal(qrH3[,1], qrR3[,1], tolerance=.03)
expect_equal(qrH4[,1], qrR4[,1], tolerance=.03)

testEnd()
}

doTest("Quantiles on Known Distributions", test.quantiles.golden)

#NOTES ON MAKEDATA 
#A: normal, mean: -100, sd = 50
#B: uniform, min: -5000, max: 2000
#C: poisson, lambda: 5
#D: cauchy, location: 50, scale: 500
#E: binom, size=100, prob=.1
#F: binom, size=100, prob=.02
#G: binom, size=10, prob=.01
#H: exponential: rate= .4
#all cols in runif are random uniform distributions with different range definitions
