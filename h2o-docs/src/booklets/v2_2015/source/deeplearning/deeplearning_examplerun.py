# Load the data and prepare for modeling
airlines_hex = h2o.import_file(path = "allyears2k_headers.csv")

# Generate random numbers and create training, validation, testing splits
r = airlines_hex.runif()   # Random UNIForm numbers, one per row
air_train_hex = airlines_hex[r  < 0.6]
air_valid_hex = airlines_hex[(r >= 0.6) & (r < 0.9)]
air_test_hex  = airlines_hex[r  >= 0.9]

myX = ["DayofMonth", "DayOfWeek"]

# Now, train the Deep Learning model:
air_model = h2o.deeplearning(y = "IsDepDelayed", x = myX, distribution="bernoulli", training_frame = air_train_hex, validation_frame = air_valid_hex,  activation = "Maxout", hidden = c(100,100))
