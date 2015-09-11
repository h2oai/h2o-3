# Re-start the training process on a saved DL model
# using the `checkpoint` argument
model_chkp = h2o.deeplearning(x=train.names[0:784],
                              y=train.names[784],
                              training_frame=train,
                              validation_frame=test,
                              checkpoint=model,
                              distribution="multinomial",
                              activation="RectifierWithDropout",
                              hidden=[200,200,200],
                              epochs=10)
