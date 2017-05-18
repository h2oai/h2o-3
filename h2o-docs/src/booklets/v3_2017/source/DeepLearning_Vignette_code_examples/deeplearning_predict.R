# Classify the test set (predict class labels)
# This also returns the probability for each class
pred <- h2o.predict(model, newdata = test)

# Take a look at the predictions
head(pred)
