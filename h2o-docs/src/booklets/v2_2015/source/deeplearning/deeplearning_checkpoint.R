# Re-start the training process on a saved DL model
# using the `checkpoint` argument
model_chkp <- h2o.deeplearning(x = 1:784, 
                               y = 785, 
                               training_frame = train, 
                               validation_frame = test,
                               distribution = "multinomial",                               
                               checkpoint = model_grid@model_ids[[1]],
                               hidden = c(200,200),
                               validation_frame = test, 
                               epochs = 10)
