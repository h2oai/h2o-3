# TO DO: Check this code

# Upload ECG train and test data
train_ecg <- h2o.importFile(path = "ecg_train.csv", 
                            header = FALSE, 
                            sep = ",", 
                            key = "train_ecg") 
test_ecg <- h2o.importFile(path = "ecg_test.csv", 
                           header = FALSE, 
                           sep = ",", 
                           key = "test_ecg") 

# Train deep autoencoder learning model on "normal" 
# training data, y ignored 
anomaly_model = h2o.deeplearning(x = 1:210, 
                                 y = 1, 
                                 valdiation_frame = train_ecg, 
                                 activation = "Tanh", 
                                 autoencoder = T, 
                                 hidden = c(50,20,50), 
                                 l1 = 1E-4, 
                                 epochs = 100)                 

# Compute reconstruction error with the Anomaly 
# detection app (MSE between output layer and input layer)
recon_error <- h2o.anomaly(test_ecg, anomaly_model)

# Pull reconstruction error data into R and 
# plot to find outliers (last 3 heartbeats)
recon_error <- as.data.frame(recon_error)
recon_error
plot.ts(recon_error)

# Note: Testing = Reconstructing the test dataset
test_recon <- h2o.predict(anomaly_model, test_ecg) 
head(test_recon)