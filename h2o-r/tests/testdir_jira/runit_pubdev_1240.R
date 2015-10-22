


test.merge.examples <- function() {
  census_path <- locate("smalldata/chicago/chicagoCensus.csv")
  
  Log.info("Import Chicago census data...")
  census_raw <- h2o.importFile(census_path, parse=FALSE)
  census_setup <- h2o.parseSetup(census_raw)
  census_setup$column_types[2] <- "Enum"  # change from String -> Enum
  census <- h2o.parseRaw(census_raw, col.types=census_setup$column_types)
  
  Log.info("Set column names to be syntactically valid for R")
  names(census) <- make.names(names(census))
  names(census)[names(census) == "Community.Area.Number"] <- "Community.Area"
  print(summary(census))
  
  Log.info("Create a small R dataframe and push to H2O")
  crimeExamples.r <- data.frame(IUCR = c(1811, 1150),
                                Primary.Type = c("NARCOTICS", "DECEPTIVE PRACTICE"),
                                Location.Description = c("STREET", "RESIDENCE"),
                                Domestic = c("false", "false"),
                                Beat = c(422, 923),
                                District = c(4, 9),
                                Ward = c(7, 14),
                                Community.Area = c(46, 63),
                                FBI.Code = c(18, 11),
                                Day = c(8, 8),
                                Month = c(2, 2),
                                Year = c(2015, 2015),
                                WeekNum = c(6, 6),
                                WeekDay = c("Sun", "Sun"),
                                HourOfDay = c(23, 23),
                                Weekend = c(1, 1),
                                Season = c(1, 1))
  crimeExamples <- as.h2o(crimeExamples.r)
  names(crimeExamples) <- make.names(names(crimeExamples))
  print(head(crimeExamples))
  
  Log.info("Merge created crime examples with Chicago census data")
  crimeExamplesMerge <- h2o.merge(crimeExamples, census)
  print(summary(crimeExamplesMerge))
  
}

doTest("Merging H2O Frames causes IllegalArgumentException", test.merge.examples)
