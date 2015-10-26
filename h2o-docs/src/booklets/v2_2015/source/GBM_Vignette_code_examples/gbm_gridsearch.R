ntrees_opt <- c(5,10,15)
maxdepth_opt <- c(2,3,4)
learnrate_opt <- c(0.1,0.2)
hyper_parameters <- list(ntrees=ntrees_opt, max_depth=maxdepth_opt, learn_rate=learnrate_opt)

grid <- h2o.grid("gbm", hyper_params = hyper_parameters, y = "IsDepDelayed", x = myX, distribution="bernoulli", training_frame = air_train.hex, validation_frame = air_valid.hex)
