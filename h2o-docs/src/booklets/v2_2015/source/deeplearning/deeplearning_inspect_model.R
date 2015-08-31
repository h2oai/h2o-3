# View the specified parameters of your deep learning model
model@parameters

# Examine the performance of the trained model
model  # display all performance metrics

h2o.performance(model, valid = FALSE)  # training set metrics
h2o.performance(model, valid = TRUE)   # validation set metrics