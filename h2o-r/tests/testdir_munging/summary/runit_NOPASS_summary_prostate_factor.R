setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.summary.factor <- function(conn) {
  Log.info("Importing prostate.csv data...\n")
  prostate.dat <- read.csv(normalizePath(locate("smalldata/logreg/prostate.csv")), header = TRUE)
  prostate.hex <- h2o.importFile(normalizePath(locate("smalldata/logreg/prostate.csv")))
  
  Log.info("Comparing R and H2O summaries...\n")
  sumR <- summary(prostate.dat)
  sumH2O <- summary(prostate.hex)
  Log.info("R Summary:"); print(sumR)
  Log.info("H2O Summary:"); print(sumH2O)
  checkSummary(sumH2O, sumR)
  
  Log.info("Convert CAPSULE, RACE, DCAPS, and DPROS columns to factors")
  myFac <- c("CAPSULE", "RACE", "DCAPS", "DPROS")
  for(col in myFac) {
    prostate.dat[,col] <- as.factor(prostate.dat[,col])
    prostate.hex[,col] <- as.factor(prostate.hex[,col])
  }
  
  sumR.fac <- summary(prostate.dat)
  sumH2O.fac <- summary(prostate.hex)
  Log.info("R Summary:"); print(sumR.fac)
  Log.info("H2O Summary:"); print(sumH2O.fac)
  checkSummary(sumH2O.fac, sumR.fac)
  testEnd()
}

doTest("Summary Test: Prostate with Conversion of Cols to Factors", test.summary.factor)
