# Import ECG train and test data into the H2O cluster
from h2o.estimators.deeplearning import H2OAutoEncoderEstimator

train_ecg = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_train.csv")
test_ecg = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_test.csv")


# Train deep autoencoder learning model on "normal" 
# training data, y ignored
anomaly_model = H2OAutoEncoderEstimator( 
        activation="Tanh", 
        hidden=[50,50,50], 
        sparse=True,
        l1=1e-4, 
        epochs=100)
anomaly_model.train(
	x=train_ecg.names,  
        training_frame=train_ecg)                

# Compute reconstruction error with the Anomaly 
# detection app (MSE between output and input layers)
recon_error = anomaly_model.anomaly(test_ecg)


# Note: Testing = Reconstructing the test dataset
test_recon = anomaly_model.predict(test_ecg) 
test_recon
