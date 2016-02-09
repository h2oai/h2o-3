ntrees_opt <- seq(1,100)
maxdepth_opt <- seq(1,10)
learnrate_opt <- seq(0.001,0.1,0.001)
hyper_parameters <- list(ntrees=ntrees_opt, 
    max_depth=maxdepth_opt, learn_rate=learnrate_opt)
search_criteria = list(strategy = "RandomDiscrete", 
    max_models = 10, max_runtime_secs=100, seed=123456)

grid <- h2o.grid("gbm", hyper_params = hyper_parameters, 
    search_criteria = search_criteria, 
    y = "IsDepDelayed", x = myX, distribution="bernoulli", 
    training_frame = air_train.hex, validation_frame = air_valid.hex)
