# Perform classification on the held out data
prediction = h2o.predict(air.model, newdata=air_test.hex)

# Copy predictions from H2O to R
pred = as.data.frame(prediction)

head(pred)
