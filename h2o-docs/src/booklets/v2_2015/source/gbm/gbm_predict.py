# Perform classification on the held out data
prediction = air_model.predict(air_test_hex)

# Copy predictions from H2O to Python
pred = prediction.as_data_frame()

pred.head()