setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.summary.factor <- function() {
  h2oTest.logInfo("Importing airlines data...\n")
  pathToData   <- normalizePath(h2oTest.locate("smalldata/airlines/allyears2k_headers.zip"))
  airlines.hex <- h2o.importFile(pathToData)
  airlines.dat <- as.data.frame(airlines.hex)
  
#   h2oTest.logInfo("Comparing R and H2O summaries...\n")
#   sumR <- summary(airlines.dat)
#   sumH2O <- summary(airlines.hex)
#   h2oTest.logInfo("R Summary:"); print(sumR)
#   h2oTest.logInfo("H2O Summary:"); print(sumH2O)
#   h2oTest.checkSummary(sumH2O, sumR)

  h2oTest.logInfo("Subset airlines dataset...\n")
  airlines.hex <- airlines.hex[airlines.hex$Year == 2005,]
  airlines.hex <- airlines.hex[airlines.hex$Origin == "ORD",]  
  airlines.dat <- as.data.frame(airlines.hex)
  
  h2oTest.logInfo("Compute and compare R and H2O Summaries...\n")
  sumR <- summary(airlines.dat)
  sumH2O <- summary(airlines.hex)
#   h2oTest.logInfo("R Summary:"); print(sumR)
#   h2oTest.logInfo("H2O Summary:"); print(sumH2O)
#   h2oTest.checkSummary(sumH2O, sumR)
  
  
}

h2oTest.doTest("Summary Test: Prostate with Conversion of Cols to Factors", test.summary.factor)
