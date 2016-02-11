hidden_opt = lapply(1:100, function(x)10+sample(50, sample(4), replace=TRUE))
l1_opt = seq(1e-6,1e-3,1e-6)
hyper_params <- list(hidden = hidden_opt, l1 = l1_opt)
search_criteria = list(strategy = "RandomDiscrete", 
    max_models = 10, max_runtime_secs = 100, 
    seed=123456)

model_grid <- h2o.grid("deeplearning", 
    grid_id = "mygrid",
    hyper_params = hyper_params, 
    search_criteria = search_criteria,
    x = x,
    y = y,
    distribution = "multinomial", 
    training_frame = train, 
    validation_frame = test,
    score_interval = 2,
    epochs = 1000,
    stopping_rounds = 3,
    stopping_tolerance = 0.05,
    stopping_metric = "misclassification")

