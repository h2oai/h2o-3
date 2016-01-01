setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.mergecat <- function() {
  census_path <- h2oTest.locate("smalldata/chicago/chicagoCensus.csv")
  crimes_path <- h2oTest.locate("smalldata/chicago/chicagoCrimes10k.csv.zip")
  
  h2oTest.logInfo("Import Chicago census data...")
  census_raw <- h2o.importFile(census_path, parse=FALSE)
  census_setup <- h2o.parseSetup(census_raw)
  census_setup$column_types[2] <- "Enum"  # change from String -> Enum
  census <- h2o.parseRaw(census_raw, col.types=census_setup$column_types)
  
  h2oTest.logInfo("Import Chicago crimes data...")
  crimes <- h2o.importFile(crimes_path)

  h2oTest.logInfo("Set column names to be syntactically valid for R")
  names(census) <- make.names(names(census))
  names(crimes) <- make.names(names(crimes))
  print(summary(census))
  print(summary(crimes))
  
  h2oTest.logInfo("Merge crimes and census data on community area number")
  names(census)[names(census) == "Community.Area.Number"] <- "Community.Area"
  crimeMerge <- h2o.merge(crimes, census, all.x=TRUE)
  print(summary(crimeMerge))
  
  
}

h2oTest.doTest("Merging H2O H2OFrames that contain categorical columns", test.mergecat)
