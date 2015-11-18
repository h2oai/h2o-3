# Perform 5-fold cross-validation on training_frame
model_cv = H2ODeepLearningEstimator(
        distribution="multinomial",
        activation="RectifierWithDropout", 
        hidden=[32,32,32],
        input_dropout_ratio=0.2, 
        sparse=True, 
        l1=1e-5, 
        epochs=10,
        nfolds=5)
model_cv.train(
        x=x, 
        y=y, 
        training_frame=train)