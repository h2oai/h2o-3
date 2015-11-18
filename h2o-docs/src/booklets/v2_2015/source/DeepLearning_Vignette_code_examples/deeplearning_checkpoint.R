# Re-start the training process on a saved DL model
# using the `checkpoint` argument
model_chkp <- h2o.deeplearning(
        x = x,
        y = y,
        training_frame = train,
        validation_frame = test,
        distribution = "multinomial",
        checkpoint = model@model_id,
        activation = "RectifierWithDropout",
        hidden = c(32,32,32),
        input_dropout_ratio = 0.2,
        sparse = TRUE,
        l1 = 1e-5,
        epochs = 20)
