# This is a demo of H2O's Random Forest (classification) function
# It imports a data set, parses it, and prints a summary
# Then, it runs RF with 50 trees, maximum depth of 100, using the iris class as the response
# Note: This demo runs H2O on localhost:54321
library(h2o)
h2o.init()

iris.hex = h2o.uploadFile(path = system.file("extdata", "iris_wheader.csv", package="h2o"), destination_frame = "iris.hex")
summary(iris.hex)
iris.rf = h2o.randomForest(y = 5, x = c(1,2,3,4), training_frame = iris.hex, ntrees = 50, max_depth = 100)
print(iris.rf)
