# Import ECG train and test data into the H2O cluster
train_ecg <- h2o.importFile(
        path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_train.csv", 
        header = FALSE, 
        sep = ",")
test_ecg <- h2o.importFile(
        path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_test.csv", 
        header = FALSE, 
        sep = ",")

# Train deep autoencoder learning model on "normal" 
# training data, y ignored 
anomaly_model <- h2o.deeplearning(
        x = names(train_ecg), 
        training_frame = train_ecg, 
        activation = "Tanh", 
        autoencoder = TRUE, 
        hidden = c(50,20,50), 
        sparse = TRUE,
        l1 = 1e-4, 
        epochs = 100)                 

# Compute reconstruction error with the Anomaly 
# detection app (MSE between output and input layers)
recon_error <- h2o.anomaly(anomaly_model, test_ecg)

# Pull reconstruction error data into R and 
# plot to find outliers (last 3 heartbeats)
recon_error <- as.data.frame(recon_error)
recon_error
plot.ts(recon_error)

# Note: Testing = Reconstructing the test dataset
test_recon <- h2o.predict(anomaly_model, test_ecg) 
head(test_recon)
