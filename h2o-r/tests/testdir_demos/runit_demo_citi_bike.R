setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Explore a typical Data Science workflow with H2O and R
#
# Goal: assist the manager of data of NYC to load-balance the bicycles
# across the data network of stations, by predicting the number of bike
# trips taken from the station every day.  Use 10 million rows of historical
# data, and eventually add weather data.

# Connect to a cluster
# Set this to True if you want to fetch the data directly from S3.
# This is useful if your cluster is running in EC2.
data_source_is_s3 = F

locate_source <- function(s) {
  if (data_source_is_s3)
    myPath <- paste0("s3n://h2o-public-test-data/", s)
  else
    myPath <- locate(s)
}

test.citibike.demo <- function() {
# Pick either the big or the small demo.
# Big data is 10M rows
small_test <-  locate_source("smalldata/demos/citibike_20k.csv")

# 1- Load data - 1 row per bicycle trip.  Has columns showing the start and end
# station, trip duration and trip start time and day.  The larger dataset
# totals about 10 million rows
print("Import and Parse bike data...")
start <- Sys.time()
data <- h2o.importFile(path = small_test, destination_frame = "citi_bike")
parseTime <- Sys.time() - start
print(paste("Took", round(parseTime, digits = 2), units(parseTime),"to parse", 
            nrow(data), "rows and", ncol(data), "columns."))

# 2- light data munging: group the bike starts per-day, converting the 10M rows
# of trips to about 140,000 station&day combos - predicting the number of trip
# starts per-station-per-day.

print('Calculate the dates and day of week based on starttime')
secsPerDay <- 1000*60*60*24
starttime  <- data$starttime
data$days  <- floor(starttime/secsPerDay)
data$year  <- year(starttime) + 1900
data$month <- month(starttime)
data$dayofweek <- dayOfWeek(starttime)
data$day   <- day(starttime)
data$age   <- data$year - data$"birth year"

print ('Group data into station & day combinations...')
start <- Sys.time()
bpd <- h2o.group_by(data, by = c("days","start station name", "year","month", "day", "dayofweek"), nrow("day") , mean("tripduration"), mean("age"))
groupTime <- Sys.time() - start
print(paste("Took", round(groupTime, digits = 2), units(groupTime), "to group", 
            nrow(data), "data points into", nrow(bpd), "points."))
names(bpd) <- c("days","start station name", "year","month", "day","dayofweek", "bike_count", "mean_duree", "mean_age")

print('Examine the distribution of the number of bike rides as well as the average day of riders per day...')
quantile(bpd$bike_count)
quantile(bpd$mean_age)
h2o.hist(bpd$bike_count)
h2o.hist(bpd$mean_age)
summary(bpd)

# 3- Fit a model on train; using test as validation

# Function for doing class test/train/holdout split
split_fit_predict <- function(data) {
  r <- h2o.runif(data$day)
  train <- data[r < 0.6,]
  test  <- data[(r >= 0.6) & (r < 0.9),]
  hold  <- data[r >= 0.9,]
  print(paste("Training data has", ncol(train), "columns and", nrow(train), "rows, test has",
              nrow(test), "rows, holdout has", nrow(hold)))
  
  myY <- "bike_count"
  myX <- setdiff(names(train), myY)

  # Run GBM
  gbm <- h2o.gbm(x = myX,
                 y = myY,
                 training_frame    = train,
                 validation_frame  = test,
                 ntrees            = 500,
                 max_depth         = 6,
                 learn_rate        = 0.1)
  
  # Run DRF
  drf <- h2o.randomForest(x = myX,
                          y = myY,
                          training_frame    = train,
                          validation_frame  = test,
                          ntrees            = 250,
                          max_depth         = 30)


  # Run GLM
  glm <- h2o.glm(x = myX,
                 y = myY,
                 training_frame    = train,
                 validation_frame  = test,
                 family            = "poisson")

  # 4- Score on holdout set & report
  train_r2_gbm  <- h2o.r2(gbm, train = TRUE)
  test_r2_gbm   <- h2o.r2(gbm, valid = TRUE)
  hold_perf_gbm <- h2o.performance(model = gbm, data = hold) 
  hold_r2_gbm   <- h2o.r2(object = hold_perf_gbm)
  print(paste0("GBM R2 TRAIN = ", train_r2_gbm, ", R2 TEST = ", test_r2_gbm, ", R2 HOLDOUT = ",
               hold_r2_gbm))

  train_r2_drf  <- h2o.r2(drf, train = TRUE)
  test_r2_drf   <- h2o.r2(drf, valid = TRUE)
  hold_perf_drf <- h2o.performance(model = drf, data = hold)
  hold_r2_drf   <- h2o.r2(object = hold_perf_drf)
  print(paste0("DRF R2 TRAIN = ", train_r2_drf, ", R2 TEST = ", test_r2_drf, ", R2 HOLDOUT = ",
               hold_r2_drf))

  train_r2_glm  <- h2o.r2(glm, train = TRUE)
  test_r2_glm   <- h2o.r2(glm, valid = TRUE)
  hold_perf_glm <- h2o.performance(model = glm, data = hold)
  hold_r2_glm   <- h2o.r2(hold_perf_glm)
  print(paste0("GLM R2 TRAIN = ", train_r2_glm, ", R2 TEST = ", test_r2_glm, ", R2 HOLDOUT = ",
               hold_r2_glm))
}

# Split the data (into test & train), fit some models and predict on the holdout data
start <- Sys.time()
split_fit_predict(bpd)
modelBuild <- Sys.time() - start
print(paste("Took", round(modelBuild, digits = 2), units(modelBuild), "to build a gbm, a random forest, and a glm model, score and report r2 values."))

# Here we see an r^2 of 0.91 for GBM, and 0.71 for GLM.  This means given just
# the station, the month, and the day-of-week we can predict 90% of the
# variance of the bike-trip-starts.

# 5- Now lets add some weather
# Load weather data
wthr1 <- h2o.importFile(path =
  c(locate_source("smalldata/demos/31081_New_York_City__Hourly_2013.csv"),
    locate_source("smalldata/demos/31081_New_York_City__Hourly_2014.csv")))

# Peek at the data
summary(wthr1)

# Lots of columns in there!  Lets plan on converting to time-since-epoch to do
# a 'join' with the bike data, plus gather weather info that might affect
# cyclists - rain, snow, temperature.  Alas, drop the "snow" column since it's
# all NA's.  Also add in dew point and humidity just in case.  Slice out just
# the columns of interest and drop the rest.
wthr2 <- wthr1[, c("Year Local","Month Local","Day Local","Hour Local","Dew Point (C)",
  "Humidity Fraction","Precipitation One Hour (mm)","Temperature (C)",
  "Weather Code 1/ Description")]
colnames(wthr2)[match("Precipitation One Hour (mm)", colnames(wthr2))] <- "Rain (mm)" # Shorter column name
names(wthr2)[match("Weather Code 1/ Description", colnames(wthr2))] <- "WC1" # Shorter column name
summary(wthr2)
# Much better!
# Filter down to the weather at Noon
wthr3 <- wthr2[ wthr2["Hour Local"]==12 ,]
# Also, most rain numbers are missing - lets assume those are zero rain days
wthr3[,"Rain (mm)"] <- ifelse(is.na(wthr3[,"Rain (mm)"]), 0, wthr3[,"Rain (mm)"])
names(wthr3) = c("year", "month", "day", names(wthr3)[4:9])


# 6 - Join the weather data-per-day to the bike-starts-per-day
print("Merge Daily Weather with Bikes-Per-Day")
bpd_with_weather <- h2o.merge(x = bpd, y = wthr3, all.x = T, all.y = F)
summary(bpd_with_weather)
print(bpd_with_weather)
dim(bpd_with_weather)

# 7 - Test/Train split again, model build again, this time with weather
start <- Sys.time()
split_fit_predict(bpd_with_weather)
modelBuild <- Sys.time() - start
print(paste("Took", round(modelBuild, digits = 2), units(modelBuild) ,"to build a gbm, a random forest, and a glm model, score and report r2 values."))
testEnd()
}

doTest("Test out Citibike Demo", test.citibike.demo)
