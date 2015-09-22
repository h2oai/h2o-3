# Train a Deep Learning model and validate on a test set
# and save the variable importances
model_vi <- h2o.deeplearning(
        x = x, 
        y = y, 
        training_frame = train,
        distribution = "multinomial",
        activation = "RectifierWithDropout", 
        hidden = c(200,200,200), 
        input_dropout_ratio = 0.2, 
        l1 = 1e-5, 
        validation_frame = test, 
        epochs = 10,
        variable_importances = TRUE)  #added

# Retrieve the variable importance
h2o.varimp(model_vi)
