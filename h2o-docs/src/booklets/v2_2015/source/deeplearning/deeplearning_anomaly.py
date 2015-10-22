# Download and import ECG train and test data into the H2O cluster
train_ecg = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_train.csv")
test_ecg = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_test.csv")


# Train deep autoencoder learning model on "normal" 
# training data, y ignored
anomaly_model = h2o.deeplearning(
        x=train_ecg.names,  
        training_frame=train_ecg, 
        activation="Tanh", 
        autoencoder=True,
        hidden=[50,50,50], 
        l1=1e-4, 
        epochs=100)                

# Compute reconstruction error with the Anomaly 
# detection app (MSE between output layer and input layer)
recon_error = anomaly_model.anomaly(test_ecg)


# Note: Testing = Reconstructing the test dataset
test_recon = anomaly_model.predict(test_ecg) 
test_recon
