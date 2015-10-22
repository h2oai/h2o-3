


test.mergecat <- function() {
  census_path <- locate("smalldata/chicago/chicagoCensus.csv")
  crimes_path <- locate("smalldata/chicago/chicagoCrimes10k.csv.zip")
  
  Log.info("Import Chicago census data...")
  census_raw <- h2o.importFile(census_path, parse=FALSE)
  census_setup <- h2o.parseSetup(census_raw)
  census_setup$column_types[2] <- "Enum"  # change from String -> Enum
  census <- h2o.parseRaw(census_raw, col.types=census_setup$column_types)
  
  Log.info("Import Chicago crimes data...")
  crimes <- h2o.importFile(crimes_path)

  Log.info("Set column names to be syntactically valid for R")
  names(census) <- make.names(names(census))
  names(crimes) <- make.names(names(crimes))
  print(summary(census))
  print(summary(crimes))
  
  Log.info("Merge crimes and census data on community area number")
  names(census)[names(census) == "Community.Area.Number"] <- "Community.Area"
  crimeMerge <- h2o.merge(crimes, census)
  print(summary(crimeMerge))
  
  
}

doTest("Merging H2O Frames that contain categorical columns", test.mergecat)
