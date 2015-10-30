# Load the data and prepare for modeling
airlines.hex <- h2o.uploadFile(path = h2o:::.h2o.locate("smalldata/airlines/allyears2k_headers.zip"), header = TRUE, sep = ",", destination_frame = "airlines.hex")

# Generate random numbers and create training, validation, testing splits
r <- h2o.runif(airlines.hex)
air_train.hex <- airlines.hex[r  < 0.6,]
air_valid.hex <- airlines.hex[(r >= 0.6) & (r < 0.9),]
air_test.hex  <- airlines.hex[r  >= 0.9,]

myX <- c("DayofMonth", "DayOfWeek")

# Now, train the GBM model:
air.model <- h2o.gbm(
         y = "IsDepDelayed", x = myX,
         distribution="bernoulli",
         training_frame = air_train.hex,
         validation_frame = air_valid.hex,
         ntrees=100, max_depth=4, learn_rate=0.1,
         sample_rate=0.6, col_sample_rate=0.7)
