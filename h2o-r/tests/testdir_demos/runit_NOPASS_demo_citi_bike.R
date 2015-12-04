setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Explore a typical Data Science workflow with H2O and R
#
# Goal: assist the manager of CitiBike of NYC to load-balance the bicycles
# across the CitiBike network of stations, by predicting the number of bike
# trips taken from the station every day.  Use 10 million rows of historical
# data, and eventually add weather data.

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

# Pick either the big or the small demo.
# Big data is 10M rows
small_test <-  locate_source("bigdata/laptop/citibike-nyc/2013-10.csv")
big_test <-  c(locate_source("bigdata/laptop/citibike-nyc/2013-07.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2013-08.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2013-09.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2013-10.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2013-11.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2013-12.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2014-01.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2014-02.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2014-03.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2014-04.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2014-05.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2014-06.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2014-07.csv"),
               locate_source("bigdata/laptop/citibike-nyc/2014-08.csv"))

# 1- Load data - 1 row per bicycle trip.  Has columns showing the start and end
# station, trip duration and trip start time and day.  The larger dataset
# totals about 10 million rows
print("Import and Parse bike data...")
data <- h2o.importFile(path =small_test, destination_frame = "citi_bike.hex")

# 2- light data munging: group the bike starts per-day, converting the 10M rows
# of trips to about 140,000 station&day combos - predicting the number of trip
# starts per-station-per-day.

# Convert start time to: Day since the Epoch
startime <- data$starttime
secsPerDay <- 1000*60*60*24
data$Days = floor(startime/secsPerDay)
summary(data)
# Now do a monster Group-By.  Count bike starts per-station per-day.  Ends up
# with about 340 stations times 400 days (140,000 rows).  This is what we want
# to predict.
group_by_cols <- c("Days", "start station name")
bpd <- h2o.group_by(data, by = group_by_cols, nrow("Days"), gb.control = list(na.methods = "all",
                                                                     col.names = "bikes"))
print(bpd)
summary(bpd)
dim(bpd)
# Quantiles: the data is fairly unbalanced; some station/day combos are wildly
# more popular than others.
print("Quantiles of bikes-per-day...")
print(quantile(bpd$bikes))
# A little feature engineering
# Add in month-of-year (seasonality; fewer bike rides in winter than summer)
secs <- bpd$Days*secsPerDay
bpd$Month    <- as.factor(month(secs))
# Add in day-of-week (work-week; more bike rides on Sunday than Monday)
bpd$DayOfWeek <- dayOfWeek(secs)
print("Bikes-Per-Day")
summary(bpd)

# 3- Fit a model on train; using test as validation

# Function for doing class test/train/holdout split
split_fit_predict <- function(data) {
  r <- h2o.runif(data$Days)
  train <- data[r < 0.6,]
  test  <- data[(0.6 <= r) & (r < 0.9),]
  hold  <- data[0.9 <= r,]
  print(paste("Training data has", ncol(train), "columns and", nrow(train), "rows, test has",
              nrow(test), "rows, holdout has," nrow(hold)))
  myX <- diff(names(train), "bikes")
  myY <- "bikes"

  # Run GBM
  gbm0 <- h2o.gbm(x                 = myX,
                  y                 = myY,
                  training_frame    = train,
                  validation_frame  = test,
                  ntrees            = 500,
                  max_depth         = 6,
                  learn_rate        = 0.1)

  # Run DRF
  drf0 <- h2o.randomForest(x                 = myX,
                           y                 = myY,
                           training_frame    = train,
                           validation_frame  = test,
                           ntrees            = 250,
                           max_depth         = 30)

  # Run GLM
  glm0 <- h2o.glm(x                 = myX,
                  y                 = myY,
                  training_frame    = train,
                  validation_frame  = test,
                  lambda            = 1e-5,
                  family            = "poisson")

  # 4- Score on holdout set & report
  train_r2_gbm <- h2o.r2(gbm0)
  test_r2_gbm  <- h2o.r2(gbm, valid = TRUE)
  hold_r2_gbm  <- h2o.r2(gbm, newdata = hold)
  print(paste0("GBM R2 TRAIN = ", train_r2_gbm, ", R2 TEST = ", test_r2_gbm, ", R2 HOLDOUT = ",
               hold_r2_gbm))

  train_r2_drf <- h2o.r2(drf0)
  test_r2_drf  <- h2o.r2(drf, valid = TRUE)
  hold_r2_drf  <- h2o.r2(drf, newdata = hold)
  print(paste0("DRF R2 TRAIN = ", train_r2_drf, ", R2 TEST = ", test_r2_drf, ", R2 HOLDOUT = ",
               hold_r2_drf))

  train_r2_glm <- h2o.r2(glm0)
  test_r2_glm  <- h2o.r2(glm, valid = TRUE)
  hold_r2_glm  <- h2o.r2(glm, newdata = hold)
  print(paste0("GLM R2 TRAIN = ", train_r2_glm, ", R2 TEST = ", test_r2_glm, ", R2 HOLDOUT = ",
               hold_r2_glm))
}
# Split the data (into test & train), fit some models and predict on the holdout data
split_fit_predict(bpd)
# Here we see an r^2 of 0.91 for GBM, and 0.71 for GLM.  This means given just
# the station, the month, and the day-of-week we can predict 90% of the
# variance of the bike-trip-starts.

# 5- Now lets add some weather
# Load weather data
wthr1 <- h2o.importFile(path =
  c(locate_source("bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2013.csv"),
    locate_source("bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2014.csv")))

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
# Lets now get Days since the epoch... we'll convert year/month/day into Epoch
# time, and then back to Epoch days.  Need zero-based month and days, but have
# 1-based.
wthr3$msec <- as.Date(paste(wthr3$"Year Local", wthr3$"Month Local", wthr3$"Day Local",
  wthr3$"Hour Local",sep = "."), format = "%Y.%m.%d.%h")
secsPerDay=1000*60*60*24
wthr3[,"Days"] <- floor(wthr3[,"msec"]/secsPerDay)
summary(wthr3)
# msec looks sane (numbers like 1.3e12 are in the correct range for msec since
# 1970).  Epoch Days matches closely with the epoch day numbers from the
# CitiBike dataset.

# Lets drop off the extra time columns to make a easy-to-handle dataset.
wthr4 <- wthr3[,-c("Year Local", "Month Local", "Day Local", "Hour Local", "msec")]
# Also, most rain numbers are missing - lets assume those are zero rain days
rain <- wthr4["Rain (mm)"]
rain[rain == None ,] = 0

# 6 - Join the weather data-per-day to the bike-starts-per-day
print "Merge Daily Weather with Bikes-Per-Day"
bpd_with_weather <- h2o.merge(x = bpd, y = wthr4,all.x = True,all.y = False)
summary(bpd_with_weather)
print(bpd_with_weather)

# 7 - Test/Train split again, model build again, this time with weather
split_fit_predict(bpd_with_weather)
