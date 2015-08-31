# TO DO: Convert to Python

# Train a Deep Learning model and validate on a test set
# and save the variable importances
# model_varimp <- h2o.deeplearning(x = 1:784, y = 785, 
#                                  training_frame = train,
#                                  distribution = "multinomial",
#                                  activation = "RectifierWithDropout", 
#                                  hidden = c(200,200,200), 
#                                  input_dropout_ratio = 0.2, 
#                                  l1 = 1e-5, 
#                                  validation_frame = test, 
#                                  epochs = 10,
#                                  variable_importances = TRUE)  # add
# 
# h2o.varimp(model_varimp)
