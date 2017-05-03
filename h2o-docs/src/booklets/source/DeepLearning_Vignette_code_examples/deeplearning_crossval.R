# Perform 5-fold cross-validation on training_frame
model_cv <- h2o.deeplearning(
        x = x, 
        y = y, 
        training_frame = train, 
        distribution = "multinomial",
        activation = "RectifierWithDropout", 
        hidden = c(32,32,32),
        input_dropout_ratio = 0.2, 
        sparse = TRUE,
        l1 = 1e-5, 
        epochs = 10,
        nfolds = 5)
