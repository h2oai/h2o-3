## Set your working directory
setwd("~/Downloads/")

# Explore a typical Data Science workflow with H2O and R
#
# Goal: assist the manager of data of NYC to load-balance the bicycles
# across the data network of stations, by predicting the number of bike
# trips taken from the station every day.  Use 10 million rows of historical
# data, and eventually add weather data.

## Load library and initialize h2o
library(h2o)
print("Launching H2O and initializing connection object...")
conn <- h2o.init(nthreads = -1)

## Find and import data into H2O
locate_source <- h2o:::.h2o.locate
small_test    <- locate_source("2013-08.csv")
big_test      <- c(locate_source("2013-07.csv"),
                   locate_source("2013-08.csv"),
                   locate_source("2013-09.csv"),
                   locate_source("2013-10.csv"),
                   locate_source("2013-11.csv"),
                   locate_source("2013-12.csv"),
                   locate_source("2014-01.csv"),
                   locate_source("2014-02.csv"),
                   locate_source("2014-03.csv"),
                   locate_source("2014-04.csv"),
                   locate_source("2014-05.csv"),
                   locate_source("2014-06.csv"),
                   locate_source("2014-07.csv"),
                   locate_source("2014-08.csv"))
print("Importing bike data into H2O...")
## Choose either to import one file with small_test or multiple monthly data with big_test
start <- Sys.time()
data <- h2o.importFile(path = small_test, destination_frame = "citibike.hex")
parseTime <- Sys.time() - start
print(paste("Took", round(parseTime, digits = 2), "seconds to parse", 
            nrow(data), "rows and", ncol(data), "columns."))

## Run some light data munging
print('Calculate the dates and day of week based on starttime')
secsPerDay <- 1000*60*60*24
starttime  <- data$starttime
data$days  <- floor(starttime/secsPerDay)
data$year  <- year(starttime) + 1900
data$month <- month(starttime)
data$dayofweek <- dayOfWeek(starttime)
data$day   <- day(starttime)
data$age   <- data$year - data$"birth year"

## Run a monster group by to get the number of bike rides per day per station
print ('Group data into station & day combinations...')
start <- Sys.time()
bpd <- h2o.group_by(data, by = c("days","start station name", "year","month", "day", "dayofweek"),
                    nrow("day") , mean("tripduration"), mean("age"))
groupTime <- Sys.time() - start
print(paste("Took", round(groupTime, digits = 2), "seconds to group", 
            nrow(data), "data points into", nrow(bpd), "points."))
names(bpd) <- c("days","start station name", "year","month", "day","dayofweek", "bike_count", "mean_duree", "mean_age")

## Find the quantile and plot the histograms to find the distribution of riders' ages.
print('Examine the distribution of the number of bike rides as well as the average day of riders per day...')
quantile(bpd$bike_count)
quantile(bpd$mean_age)
h2o.hist(bpd$bike_count)
h2o.hist(bpd$mean_age)

## Function that will do a test, trian, and hold-out split and builds GLM, GBM, Random Forest, and Deep Learning models.
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
  gbm <- h2o.gbm(x = myX, build_tree_one_node = T, 
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
                 lambda            = 1e-5,
                 family            = "poisson")

  # 4- Score on holdout set & report
  train_r2_gbm <- h2o.r2(gbm)
  test_r2_gbm  <- h2o.r2(gbm, valid = TRUE)
  hold_r2_gbm  <- h2o.r2(gbm, newdata = hold)
  print(paste0("GBM R2 TRAIN = ", train_r2_gbm, ", R2 TEST = ", test_r2_gbm, ", R2 HOLDOUT = ",
               hold_r2_gbm))

  train_r2_drf <- h2o.r2(drf)
  test_r2_drf  <- h2o.r2(drf, valid = TRUE)
  hold_r2_drf  <- h2o.r2(drf, newdata = hold)
  print(paste0("DRF R2 TRAIN = ", train_r2_drf, ", R2 TEST = ", test_r2_drf, ", R2 HOLDOUT = ",
               hold_r2_drf))

  train_r2_glm <- h2o.r2(glm)
  test_r2_glm  <- h2o.r2(glm, valid = TRUE)
  hold_r2_glm  <- h2o.r2(glm, newdata = hold)
  print(paste0("GLM R2 TRAIN = ", train_r2_glm, ", R2 TEST = ", test_r2_glm, ", R2 HOLDOUT = ",
               hold_r2_glm))
}

## Run through model build
start <- Sys.time()
split_fit_predict(bpd)
modelBuild <- Sys.time() - start
print(paste("Took", round(modelBuild, digits = 2), units(modelBuild),"to build a gbm, a random forest, and a glm model, score and report r2 values."))

## Add in the weather component
wthr_path <-   c(locate_source("31081_New_York_City__Hourly_2013.csv"),
                 locate_source("31081_New_York_City__Hourly_2014.csv"))
print("Importing 2013 and 2014 weather into H2O...")
wthr1     <- h2o.importFile(path = wthr_path)

## Change column names to make it easier to manipulate
wthr2     <- wthr1[, c("Year Local","Month Local","Day Local","Hour Local","Dew Point (C)",
                       "Humidity Fraction","Precipitation One Hour (mm)","Temperature (C)",
                       "Weather Code 1/ Description")]
names(wthr2)[match("Precipitation One Hour (mm)", colnames(wthr2))] <- "Rain (mm)" # Shorter column name
names(wthr2)[match("Weather Code 1/ Description", colnames(wthr2))] <- "WC1" # Shorter column name
head(wthr2)

## Filter down the dataset to only the time at noon to be representative of the entire day's weather.
wthr3 <- wthr2[ wthr2["Hour Local"]==12 ,]
## Also, most rain numbers are missing - lets assume those are zero rain days
wthr3[,"Rain (mm)"] <- ifelse(is.na(wthr3[,"Rain (mm)"]), 0, wthr3[,"Rain (mm)"])
names(wthr3)        <- c("year", "month", "day", names(wthr3)[4:9])

## Join the weather data-per-day to the bike-starts-per-day
print("Merge Daily Weather with Bikes-Per-Day")
bpd_with_weather <- h2o.merge(x = bpd, y = wthr3, all.x = T, all.y = F)
head(bpd_with_weather)
dim(bpd_with_weather)

# Test/Train split again, model build again, this time with weather
start <- Sys.time()
split_fit_predict(bpd_with_weather)
modelBuild <- Sys.time() - start
print(paste("Took", round(modelBuild, digits = 2), units(modelBuild),"to build a gbm, a random forest, and a glm model, score and report r2 values."))
