import h2o
h2o.init()

train_file = "https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/train.csv.gz"
test_file = "https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/test.csv.gz"

train = h2o.import_frame(train_file)
test = h2o.import_frame(test_file)

# To see a brief summary of the data, run the following command
train.describe()
test.describe()
