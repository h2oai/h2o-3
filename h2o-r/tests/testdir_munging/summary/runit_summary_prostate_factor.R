setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.summary.factor <- function() {
  h2oTest.logInfo("Importing prostate.csv data...\n")
  prostate.dat <- read.csv(normalizePath(h2oTest.locate("smalldata/logreg/prostate.csv")), header = TRUE)
  prostate.hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/logreg/prostate.csv")))
  
  h2oTest.logInfo("Comparing R and H2O summaries...\n")
  sumR <- summary(prostate.dat)
  sumH2O <- summary(prostate.hex)
  h2oTest.logInfo("R Summary:"); print(sumR)
  h2oTest.logInfo("H2O Summary:"); print(sumH2O)
  h2oTest.checkSummary(sumH2O, sumR)
  
  h2oTest.logInfo("Convert CAPSULE, RACE, DCAPS, and DPROS columns to factors")
  myFac <- c("CAPSULE", "RACE", "DCAPS", "DPROS")
  for(col in myFac) {
    prostate.dat[,col] <- as.factor(prostate.dat[,col])
    prostate.hex[,col] <- as.factor(prostate.hex[,col])
  }
  
  sumR.fac <- summary(prostate.dat)
  sumH2O.fac <- summary(prostate.hex)
  h2oTest.logInfo("R Summary:"); print(sumR.fac)
  h2oTest.logInfo("H2O Summary:"); print(sumH2O.fac)
  h2oTest.checkSummary(sumH2O.fac, sumR.fac)
  
}

h2oTest.doTest("Summary Test: Prostate with Conversion of Cols to Factors", test.summary.factor)
