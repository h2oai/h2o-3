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
        input_dropout_ratio=0.2,
        sparse=True,
        l1=1e-5,
        epochs=20)
