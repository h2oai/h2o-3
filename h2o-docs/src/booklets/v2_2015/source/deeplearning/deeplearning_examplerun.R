# Load the data and prepare for modeling
airlines.hex <- h2o.uploadFile(h2o_server, path = "allyears2k_headers.csv", header = TRUE, sep = ",", destination_frame = "airlines.hex")

# Generate random numbers and create training, validation, testing splits
r <- h2o.runif(airlines.hex)
air_train.hex <- airlines.hex[r  < 0.6,]
air_valid.hex <- airlines.hex[(r >= 0.6) & (r < 0.9),]
air_test.hex  <- airlines.hex[r  >= 0.9,]

myX <- c("DayofMonth", "DayOfWeek")

# Now, train the Deep Learning model:
air.model <- h2o.deeplearning(y = "IsDepDelayed", x = myX, distribution="bernoulli", training_frame = air_train.hex, validation_frame = air_valid.hex, activation = "Maxout", hidden = c(100,100)))
