# View the specified parameters of your deep learning model
model.params

# Examine the performance of the trained model
model  # display all performance metrics

model.model_performance(train=True)  # training set metrics
model.model_performance(valid=True)  # validation set metrics

# Get MSE only
model.mse(valid=True)

# Cross-validated MSE
model_cv.mse(xval=True)