# Perform classification on the test set
# This also returns the probability for each class
prediction <- h2o.predict(model, newdata = test)

# If desired, you can copy predictions from H2O to R
pred  <- as.data.frame(prediction)
