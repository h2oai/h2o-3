import h2o
h2o.init()  # Will set up H2O cluster using all available cores

train = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/train.csv.gz")
test = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/test.csv.gz")

# To see a brief summary of the data, run the following command
train.describe()
test.describe()
