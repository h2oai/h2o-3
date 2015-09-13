# Perform 5-fold cross-validation on the training_frame
model_cv = h2o.deeplearning(x=x, 
                            y=y, 
                            training_frame=train, 
                            distribution="multinomial",
                            activation="RectifierWithDropout", 
                            hidden=[200,200,200], 
                            input_dropout_ratio=0.2, 
                            l1=1e-5, 
                            epochs=10,
                            nfolds=5)