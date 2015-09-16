import h2o
h2o.init()  # Will set up H2O cluster using all available cores

train_file = "https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/train.csv.gz"
test_file = "https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/test.csv.gz"

train = h2o.import_file(train_file)
test = h2o.import_file(test_file)

# To see a brief summary of the data, run the following command
train.describe()
test.describe()
