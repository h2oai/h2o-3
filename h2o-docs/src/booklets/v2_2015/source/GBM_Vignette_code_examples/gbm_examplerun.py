# Now, train the GBM model:
from h2o.estimators.gbm import H2OGradientBoostingEstimator

# Load the data and prepare for modeling
airlines_hex = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/airlines/allyears2k_headers.zip")

# Generate random numbers and create training, validation, testing splits
r = airlines_hex.runif()   # Random UNIForm numbers, one per row
air_train_hex = airlines_hex[r  < 0.6]
air_valid_hex = airlines_hex[(r >= 0.6) & (r < 0.9)]
air_test_hex  = airlines_hex[r  >= 0.9]

myX = ["DayofMonth", "DayOfWeek"]

air_model = H2OGradientBoostingEstimator(
               distribution='bernoulli', ntrees=100,
               max_depth=4, learn_rate=0.1)
air_model.train(x=myX, y="IsDepDelayed",
                training_frame=air_train_hex)