hidden_opt <- list(c(32,32), c(32,16,8), c(100))
l1_opt <- c(1e-4,1e-3)
hyper_params <- list(hidden = hidden_opt, l1 = l1_opt)

model_grid <- h2o.grid(
        "deeplearning", 
        hyper_params = hyper_params, 
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

