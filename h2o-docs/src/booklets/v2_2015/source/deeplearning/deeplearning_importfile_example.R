library(h2o)
h2o.init(nthreads = -1)  # This means nthreads = num available cores

train_file <- "https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/train.csv.gz"
test_file <- "https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/mnist/test.csv.gz"

train <- h2o.importFile(train_file, header = FALSE, sep = ",")
test <- h2o.importFile(test_file, header = FALSE, sep = ",")

# To see a brief summary of the data, run the following command
summary(train)
summary(test)
