setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Try to slice by using != factor_level
#----------------------------------------------------------------------

options(echo=TRUE)
library(h2o)
check.revalue <- function() {

  filePath <- "/home/0xdiag/datasets/airlines/airlines_all.csv"

  # Uploading data file to h2o.
  air <- h2o.importFile(filePath, "air")

  # Print dataset size.
  print(levels(air$Origin))

  revalue(air$Origin, c(SFO = "SAN FRANCISCO TREAT AIRPOT"))

  print(levels(air$Origin))


  
}

h2oTest.doTest("Slice using != factor_level test", check.revalue)
