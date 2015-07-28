## Set your working directory
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))

## Load library and initialize h2o
library(h2o)
print("Launching H2O and initializing connection object...")
conn <- h2o.init(nthreads = -1)

## Find and import data into H2O
locate       <- h2o:::.h2o.locate
pathToData   <- locate("airlines_all.05p.csv")
pathToWeatherData <- locate("Chicago_Ohare_International_Airport.csv")
print("Importing airlines dataset into H2O...")
airlines.hex <- h2o.importFile(path = pathToData, destination_frame = "airlines.hex")
weather.hex  <- h2o.importFile(path = pathToWeatherData, destination_frame = "weather.hex")

## Grab a summary of imported frame
summary(airlines.hex)
summary(weather.hex)

## Look at the distribution of flights per Year, per Month
h2o.hist(airlines.hex$Year)
h2o.hist(airlines.hex$Month)

## Create scatter plots by taking a random sample into R to plot and graphing linear fit
scatter_plot <- function(data, x, y, max_points = 1000, fit = T) {
  if (fit) {
    lr <- h2o.glm(x = x, y = y, training_frame = data, family = "gaussian")
    coeff <- lr@model$coefficients_table$coefficients    
  }
  
  df <- data[,c(x, y)]
  
  runif <- h2o.runif(df)
  df.subset <- df[runif < max_points/nrow(data),]
  df.R <- as.data.frame(df.subset)
  h2o.rm(df.subset)
  if (fit) h2o.rm(data@conn, lr@model_id)
  
  plot(x = df.R[,x], y = df.R[,y], col = "yellow", xlab = x, ylab = y)
  if (fit) abline(coef = coeff, col = "black")
}

scatter_plot(data = airlines.hex, x = "Distance", y = "AirTime", fit = T)
scatter_plot(data = airlines.hex, x = "UniqueCarrier", y = "ArrDelay", max_points = 5000, fit = F)

## Parameter Creation
hour1 <- airlines.hex$CRSArrTime %/% 100
mins1 <- airlines.hex$CRSArrTime %% 100
arrTime <- hour1*60+mins1

hour2 <- airlines.hex$CRSDepTime %/% 100
mins2 <- airlines.hex$CRSDepTime %% 100
depTime <- hour2*60+mins2

travelTime <- ifelse(arrTime - depTime > 0, arrTime - depTime, NA)
airlines.hex$TravelTime <- travelTime
scatter_plot(airlines.hex, "Distance", "TravelTime")

## Imputation : You can also choose to impute missing values by taking the mean of subsets.
airlines.hex <- h2o.impute(data = airlines.hex, column = "Distance", by = c("Origin","Dest"))
scatter_plot(airlines.hex, "Distance", "TravelTime")

## Clean weather dataset, convert dates column to multiple year, month, day columns
weather.hex$Date <- as.Date(weather.hex$Date, format = "%m/%d/%Y")
weather.hex$Year <- h2o.year(weather.hex$Date) + 1900
weather.hex$Month <- h2o.month(weather.hex$Date)
weather.hex$DayofMonth <- h2o.day(weather.hex$Date)

## Filter airlines dataset to only flights from Ohare, Chicago
airlines.hex <- airlines.hex[airlines.hex$Origin=="ORD",]
airlines.hex <- airlines.hex[airlines.hex$Year == 2005,]

## Join the airlines dataset with the weather data
joined_table <- h2o.merge(x = airlines.hex, y = weather.hex, all.x = T)


## Set Column Type for Enumerator or Factor Columns
joined_table$Year      <- as.factor(joined_table$Year)
joined_table$Month     <- as.factor(joined_table$Month)
joined_table$DayOfWeek <- as.factor(joined_table$DayOfWeek)
joined_table$Cancelled <- as.factor(joined_table$Cancelled)

#####################################################################################################################

## Create test/train split
data.split <- h2o.splitFrame(data = joined_table, ratios = 0.8)
data.train <- data.split[[1]]
data.test <- data.split[[2]]

# Set predictor and response variables
myY <- "IsDepDelayed"
myX <- c("Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum")

## Build GLM 
start    <- Sys.time()
data.glm <- h2o.glm(y = myY, x = myX, training_frame = data.train, validation_frame = data.test, family = "binomial", 
                    standardize=T, model_id = "glm_model", alpha = 0.5, lambda = 1e-05)
glm_time <- Sys.time() - start
print(paste("Took", round(glm_time, digits = 2), units(glm_time), "to build logistic regression model."))

## Build GBM Model
start    <- Sys.time()
data.gbm <- h2o.gbm(y = myY, x = myX, balance_classes = T, training_frame = data.train, validation_frame = data.test,
                    ntrees = 100, max_depth = 5, model_id = "gbm_model", distribution = "bernoulli", learn_rate = .1,
                    min_rows = 2)
gbm_time <- Sys.time() - start
print(paste("Took", round(gbm_time, digits = 2), units(gbm_time), "to build a GBM model."))

## Build Random Forest Model
start    <- Sys.time()
data.drf <- h2o.randomForest(y = myY, x = myX, training_frame = data.train, validation_frame = data.test, ntrees = 150,
                             max_depth = 5, model_id = "drf_model", balance_classes = T)
drf_time <- Sys.time() - start
print(paste("Took", round(drf_time, digits = 2), units(drf_time), "to build a Random Forest model."))
  
## Build Deep Learning Model
start   <- Sys.time()
data.dl <- h2o.deeplearning(y = myY, x = myX, training_frame = data.train, validation_frame = data.test, hidden=c(10, 10),
                            epochs = 5, balance_classes = T, loss = "Automatic", variable_importances = T)
dl_time <- Sys.time() - start
print(paste("Took", round(dl_time, digits = 2), units(dl_time), "to build a Deep Learning model."))

## Variable Importance - For feature selection and rerunning a model build
print("GLM: Sorted Standardized Coefficient Magnitudes To Find Nonzero Coefficients")
data.glm@model$standardized_coefficients_magnitude
print("GBM: Variable Importance")
data.gbm@model$variable_importances
print("Random Forest: Variable Importance")
data.drf@model$variable_importances
print("Deep Learning: Variable Importance")
data.dl@model$variable_importances
