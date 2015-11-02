library(h2o)

# Sets number of threads to number of available cores
h2o.init(nthreads = -1)

train_file <- "https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/train.csv.gz"
test_file <- "https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/test.csv.gz"

train <- h2o.importFile(train_file)
test <- h2o.importFile(test_file)

# Get a brief summary of the data
summary(train)
summary(test)
