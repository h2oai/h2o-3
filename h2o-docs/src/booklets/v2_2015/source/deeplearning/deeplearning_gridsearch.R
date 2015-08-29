activation_opt <- c("Tanh", "Maxout")
hidden_opt <- list(c(100,100), c(100,200,100))
hyper_parameters <- list(activation = activation_opt, hidden = hidden_opt)

grid <- h2o.grid("deeplearning", hyper_params = hyper_parameters, y = "IsDepDelayed", x = myX, distribution="bernoulli", training_frame = air_train.hex, validation_frame = air_valid.hex)
