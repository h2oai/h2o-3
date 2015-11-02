iris.gbm2 <- h2o.gbm(y = 5, x = 1:4, training_frame = iris.hex, ntrees = 15, max_depth = 5, min_rows = 2,
                     learn_rate = 0.01, distribution= "multinomial")

iris.gbm2@model$training_metrics