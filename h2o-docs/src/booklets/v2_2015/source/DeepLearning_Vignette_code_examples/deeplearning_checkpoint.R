# Re-start the training process on a saved DL model
# using the `checkpoint` argument
model_chkp <- h2o.deeplearning(
        x = x, 
        y = y, 
        training_frame = train, 
        validation_frame = test,
        distribution = "multinomial",                               
        checkpoint = model_grid@model_ids[[1]],
        hidden = c(100, 300, 100),
        epochs = 20)
