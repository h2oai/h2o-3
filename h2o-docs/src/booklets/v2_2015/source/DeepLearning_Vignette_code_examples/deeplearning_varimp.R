# Train Deep Learning model and validate on test set
# and save the variable importances
model_vi <- h2o.deeplearning(
        x = x, 
        y = y, 
        training_frame = train,
        distribution = "multinomial",
        activation = "RectifierWithDropout", 
        hidden = c(32,32,32),
        input_dropout_ratio = 0.2, 
        sparse = TRUE,
        l1 = 1e-5, 
        validation_frame = test, 
        epochs = 10,
        variable_importances = TRUE)

# Retrieve the variable importance
h2o.varimp(model_vi)
