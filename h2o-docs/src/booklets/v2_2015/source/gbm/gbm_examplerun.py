# Load the data and prepare for modeling
air_train_hex = h2o.uploadFile(h2o_server, path = "AirlinesTrain.csv", header = TRUE, sep = ",", key = "airline_train.hex")

air_test_hex = h2o.uploadFile(h2o_server, path = "AirlinesTest.csv", header = TRUE, sep = ",", key = "airline_test.hex")

myX = ["fDayofMonth", "fDayOfWeek"]


# Now, train the GBM model:
air_model = h2o.gbm(y = "IsDepDelayed", x = myX, distribution="multinomial", data = air_train.hex, n.trees=100, interaction.depth=4, shrinkage=0.1, importance=TRUE)
