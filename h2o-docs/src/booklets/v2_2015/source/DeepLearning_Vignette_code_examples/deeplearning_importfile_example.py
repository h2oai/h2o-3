import h2o

# Start H2O cluster with all available cores (default)
h2o.init() 

train = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/train.csv.gz")
test = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/test.csv.gz")

# Get a brief summary of the data
train.describe()
test.describe()
