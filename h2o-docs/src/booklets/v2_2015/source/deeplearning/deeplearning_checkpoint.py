# Re-start the training process on a saved DL model
# using the `checkpoint` argument
model_chkp = h2o.deeplearning(
        x=x,
        y=y,
        training_frame=train,
        validation_frame=test,
        checkpoint=model,
        distribution="multinomial",
        activation="RectifierWithDropout",
        hidden=[200,200,200],
        epochs=10)
