# Perform 5-fold cross-validation on the training_frame
model_cv = H2ODeepLearningEstimator(
        distribution="multinomial",
        activation="RectifierWithDropout", 
        hidden=[200,200,200], 
        input_dropout_ratio=0.2, 
        sparse=True, 
        l1=1e-5, 
        epochs=10,
        nfolds=5)
model_cv.train(
        x=x, 
        y=y, 
        training_frame=train)