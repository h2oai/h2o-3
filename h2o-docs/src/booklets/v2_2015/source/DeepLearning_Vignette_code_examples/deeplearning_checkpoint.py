# Re-start the training process on a saved DL model
# using the `checkpoint` argument
model_chkp = H2ODeepLearningEstimator(
        checkpoint=model,
        distribution="multinomial",
        activation="RectifierWithDropout",
        hidden=[32,32,32],
        input_dropout_ratio=0.2,
        sparse=True,
        l1=1e-5,
        epochs=20)

model_chkp.train(        
        x=x,
        y=y,
        training_frame=train,
        validation_frame=test)