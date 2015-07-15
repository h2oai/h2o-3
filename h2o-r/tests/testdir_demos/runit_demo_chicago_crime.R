setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Connect to a cluster
# Set this to True if you want to fetch the data directly from S3.
# This is useful if your cluster is running in EC2.
data_source_is_s3 = FALSE

locate_source <- function(s) {
  if (data_source_is_s3)
    myPath <- paste0("s3n://h2o-public-test-data/", s)
  else
    myPath <- locate(s)
}

# Takes column in the specified format 'MM/dd/yyyy hh:mm:ss a' and refines
# it into 8 columns: "Day", "Month", "Year", "WeekNum", "WeekDay", "Weekend",
# "Season", "HourOfDay"
ComputeDateCols <- function(col, datePattern, dateTimeZone = "Etc/UTC") {
  if(nzchar(dateTimeZone) > 0) h2o.setTimezone(dateTimeZone)
  d <- as.Date(col, format = datePattern)
  ds <- c(Day = h2o.day(d), Month = h2o.month(d), Year = h2o.year(d), WeekNum = h2o.week(d),
    WeekDay = h2o.dayOfWeek(d), HourOfDay = h2o.hour(d))
  
  # Indicator column of whether day is on the weekend
  ds$Weekend <- ifelse(ds$WeekDay == "Sun" | ds$WeekDay == "Sat", 1, 0)
  # ds$Weekend <- as.factor(ds$Weekend)
  
  # Categorical column of season: Spring = 0, Summer = 1, Autumn = 2, Winter = 3
  ds$Season <- ifelse(ds$Month >= 2 & ds$Month <= 4, 0,        # Spring = Mar, Apr, May
               ifelse(ds$Month >= 5 & ds$Month <= 7, 1,        # Summer = Jun, Jul, Aug
               ifelse(ds$Month >= 8 & ds$Month <= 9, 2, 3)))   # Autumn = Sep, Oct
  ds$Season <- as.factor(ds$Season)
  h2o.setLevels(ds$Season, c("Spring", "Summer", "Autumn", "Winter"))
  # ds$Season <- cut(ds$Month, breaks = c(-1, 1, 4, 6, 9, 11), labels = c("Winter", "Spring", "Summer", "Autumn", "Winter"))
  return(ds)
}

RefineDateColumn <- function(train, dateCol, datePattern, dateTimeZone = "Etc/UTC") {
  refinedDateCols <- ComputeDateCols(train[,dateCol], datePattern, dateTimeZone)
  # mapply(function(val, nam) { do.call("$<-", list(train, nam, val)) }, refinedDateCols, names(refinedDateCols))
  train$Day <- refinedDateCols$Day
  train$Month <- refinedDateCols$Month + 1    # Since start counting from 0
  train$Year <- refinedDateCols$Year + 1900   # Since indexed starting from 1900
  train$WeekNum <- refinedDateCols$WeekNum
  train$WeekDay <- refinedDateCols$WeekDay
  train$HourOfDay <- refinedDateCols$HourOfDay
  train$Weekend <- refinedDateCols$Weekend
  train$Season <- refinedDateCols$Weekend
  train
}

test.chicago.demo <- function() {
  weather_path <- locate_source("smalldata/chicago/chicagoAllWeather.csv")
  census_path <- locate_source("smalldata/chicago/chicagoCensus.csv")
  crimes_path <- locate_source("smalldata/chicago/chicagoCrimes10k.csv.zip")
  
  Log.info("Import and parse data...")
  weather <- h2o.importFile(path=weather_path, destination_frame="weather.hex")
  crimes <- h2o.importFile(path=crimes_path, destination_frame="crimes.hex")
  # census <- h2o.importFile(path=census_path, destination_frame="census.hex")
  
  # TODO: Get rid of this once merging with string cols is supported. See PUBDEV-1188.
  census_raw <- h2o.importFile(census_path, parse = FALSE)
  census_setup <- h2o.parseSetup(census_raw)
  census_setup$column_types[2] <- "Enum"   # Change community area name col from string to enum
  census <- h2o.parseRaw(census_raw, col.types = census_setup$column_types)
  
  Log.info("Set columns names to be syntactically valid in R")
  names(census) <- make.names(names(census))
  names(crimes) <- make.names(names(crimes))
  
  Log.info("Replace Date column with Year, Month, Day, Week, Hour, Weekend and Season")
  crimes <- RefineDateColumn(crimes, which(colnames(crimes) == "Date"), datePattern = "%m/%d/%Y %I:%M:%S %p")
  crimes$Date <- NULL   # Remove redundant date columns
  weather$date <- NULL
  
  Log.info("Merge crimes and census data on community area number")
  names(census)[names(census) == "Community.Area.Number"] <- "Community.Area"
  crimeMerge <- h2o.merge(crimes, census)
  Log.info("Merge crimes and weather data on month, day and year")
  names(weather)[match(c("month", "day", "year"), names(weather))] <- c("Month", "Day", "Year")
  crimeMerge <- h2o.merge(crimeMerge, weather)

  Log.info("Split final dataset into test/train (ratio = 20/80)")
  # BUG: h2o.splitFrame call causes an NPE. See PUBDEV-1235.
  # frs <- h2o.splitFrame(crimeMerge, ratios = c(0.8,0.2))
  # train <- frs[1]
  # test <- frs[2]
  split <- h2o.runif(crimeMerge)      # Useful when number of rows too large for R to handle
  train <- crimeMerge[split <= 0.8,]
  test <- crimeMerge[split > 0.8,]
  
  Log.info("Build a GBM model and score")
  myY <- "Arrest"
  myX <- setdiff(1:ncol(train), which(colnames(train) == myY))
  gbmModel <- h2o.gbm(x = myX, y = myY, training_frame = train, validation_frame = test, ntrees = 10, max_depth = 6, distribution = "bernoulli")
  
  Log.info("Build a Deep Learning model and score")
  dlModel <- h2o.deeplearning(x = myX, y = myY, training_frame = train, validation_frame = test, variable_importances = TRUE)
  
  cat("\nModel performance:")
  cat("\n\tGBM:\n\t\ttrain AUC = ", gbmModel@model$training_metric@metrics$AUC)
  cat("\n\t\ttest AUC = ", gbmModel@model$validation_metric@metrics$AUC)
  cat("\n\tDL:\n\t\ttrain AUC = ", dlModel@model$training_metric@metrics$AUC)
  cat("\n\t\ttest AUC = ", dlModel@model$validation_metric@metrics$AUC, "\n")
  
  Log.info("Predict on new crime data")
  crimeExamples.r <- data.frame(Date = c("02/08/2015 11:43:58 PM", "02/08/2015 11:00:39 PM"),
                                IUCR = c(1811, 1150),
                                Primary.Type = c("NARCOTICS", "DECEPTIVE PRACTICE"),
                                Location.Description = c("STREET", "RESIDENCE"),
                                Domestic = c("false", "false"),
                                Beat = c(422, 923),
                                District = c(4, 9),
                                Ward = c(7, 14),
                                Community.Area = c(46, 63),
                                FBI.Code = c(18, 11))
  crimeExamples <- as.h2o(crimeExamples.r)
  names(crimeExamples) <- make.names(names(crimeExamples))
  crimeExamples <- RefineDateColumn(crimeExamples, which(colnames(crimeExamples) == "Date"), datePattern = "%m/%d/%Y %I:%M:%S %p")
  crimeExamples$Date <- NULL   # Remove redundant date columns
  crimeExamplesMerge <- h2o.merge(crimeExamples, census)
  
  predGBM <- predict(gbmModel, crimeExamplesMerge)
  predDL <- predict(dlModel, crimeExamplesMerge)
  for(i in 1:nrow(crimeExamples)) {
    cat("\nCrime:\n"); print(crimeExamples[i,])
    cat("\n\tProbability of arrest using GBM:", as.matrix(predGBM$true[i]))
    cat("\n\tProbability of arrest using Deep Learning:", as.matrix(predDL$true[i]), "\n")
  }
  testEnd()
}

doTest("Test out Chicago Crime Demo", test.chicago.demo)