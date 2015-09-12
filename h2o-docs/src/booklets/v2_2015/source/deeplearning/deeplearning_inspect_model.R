# View the specified parameters of your deep learning model
model@parameters

# Examine the performance of the trained model
model  # display all performance metrics

h2o.performance(model, train = TRUE)  # training set metrics
h2o.performance(model, valid = TRUE)  # validation set metrics

# Get MSE only
h2o.mse(model, valid = TRUE)

# Cross-validated MSE
h2o.mse(model_cv, xval = TRUE)