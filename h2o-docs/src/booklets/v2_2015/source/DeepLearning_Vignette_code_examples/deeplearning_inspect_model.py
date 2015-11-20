# View specified parameters of the Deep Learning model
model.params

# Examine the performance of the trained model
model  # display all performance metrics

model.model_performance(train=True)  # training metrics
model.model_performance(valid=True)  # validation metrics

# Get MSE only
model.mse(valid=True)

# Cross-validated MSE
model_cv.mse(xval=True)