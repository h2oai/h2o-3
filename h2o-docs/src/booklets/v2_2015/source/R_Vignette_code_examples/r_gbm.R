library(h2o)
h2o.init(nthreads = -1)
data(iris)
iris.hex <- as.h2o(iris,destination_frame = "iris.hex")
iris.gbm <- h2o.gbm(y = 1, x = 2:5, training_frame = iris.hex, ntrees = 10,
                    max_depth = 3,min_rows = 2, learn_rate = 0.2, distribution= "gaussian")

# To obtain the Mean-squared Error by tree from the model object:
iris.gbm@model$scoring_history
