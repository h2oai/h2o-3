# Re-start the training process on a saved DL model
# using the `checkpoint` argument
model_chkp <- h2o.deeplearning(
        x = x, 
        y = y, 
        training_frame = train, 
        validation_frame = test,
        distribution = "multinomial",                               
        checkpoint = model@model_id,
        hidden = c(200,200,200),
        epochs = 20)
