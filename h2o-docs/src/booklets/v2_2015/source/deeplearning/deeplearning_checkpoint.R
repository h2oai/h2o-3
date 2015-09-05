# TO DO: Check this code

model_chkp <- h2o.deeplearning(x = 1:784, y = 785, 
                               training_frame = train, 
                               checkpoint = model_grid@model_ids[[1]],
                               hidden = c(100,100),
                               activation = "Tanh",
                               validation_frame = test, 
                               epochs = 9)