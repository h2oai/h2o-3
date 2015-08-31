# Perform classification on the held out data
prediction = model.predict(test)

# Copy predictions from H2O to Python
pred = prediction.as_data_frame()

pred.head()
