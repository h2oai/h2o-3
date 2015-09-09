activation_opt <- c("Tanh", "Rectifier")
hidden_opt <- list(c(100,100), c(100,200,100))
hyper_params <- list(activation = activation_opt, 
                     hidden = hidden_opt)

model_grid <- h2o.grid("deeplearning", 
                       hyper_params = hyper_params, 
                       x = 1:784,
                       y = 785,
                       distribution = "bernoulli", 
                       training_frame = train, 
                       validation_frame = test)

