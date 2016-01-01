setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_anomaly_mse <- function(conn) {
  h2oTest.logInfo("Deep Learning Anomaly Detection on ECG Data)")
  TRAIN <- h2oTest.locate("smalldata/anomaly/ecg_discord_train.csv")
  
  ecg_original <- h2o.uploadFile(path = TRAIN, destination_frame = "ecg_original.hex")
  
  # Build autoencoder model
  ecg_anomaly <- h2o.deeplearning(x=1:210, training_frame = ecg_original, activation = "Tanh", 
                                  autoencoder=T, hidden = c(50), l1 = 1E-4, epochs=5)
  # Reconstruct original frame with autoencoder model.
  ecg_recreate <- h2o.predict(object = ecg_anomaly, newdata = ecg_original)
  ecg_mse      <- h2o.anomaly(ecg_anomaly, ecg_original, per_feature = F)
  
  # Normalize Original and Recreated H2OFrame
  ecg_original.R <- as.data.frame(ecg_original)
  ecg_recreate.R <- as.data.frame(ecg_recreate)
  
  s <- apply(ecg_original.R, 2, max) - apply(ecg_original.R, 2, min)
  m <- apply(ecg_original.R, 2, mean)
  
  original_scaled <- t(apply(ecg_original.R, 1, function(x) (x-m)/s))
  recreate_scaled <- t(apply(ecg_recreate.R, 1, function(x) (x-m)/s))
  
  # Create reconstructed squared error for each feature
  reconstructed_err1 <- (original_scaled - recreate_scaled)^2
  # h2o.anomaly computes the per-row per-feature reconstruction error for the training data set
  reconstructed_err2 <- as.matrix(h2o.anomaly(ecg_anomaly, ecg_original, per_feature = T)) 
  
  # Check to make sure the per feature squared error is correctly calculated in h2o.anomaly
  checkEqualsNumeric(reconstructed_err1, reconstructed_err2)
  # Check to make sure the per feature squared error computes the correct MSE
  mse1 <- apply(reconstructed_err2, 1, sum)/210
  mse2 <- as.matrix(ecg_mse)[,1]
  checkEqualsNumeric(mse1, mse2)
    
  
  
  
  h2oTest.logInfo("Deep Learning Anomaly Detection on Iris Data with Categoricals)")
  
  iris_original <- as.h2o(iris)
  
  # Build autoencoder model
  iris_anomaly <- h2o.deeplearning(x=1:5, training_frame = iris_original, activation = "Tanh", 
                                  autoencoder=T, hidden = c(10), l1 = 1E-4, epochs=5)
  # Reconstruct original frame with autoencoder model.
  iris_recreate <- h2o.predict(object = iris_anomaly, newdata = iris_original)
  iris_mse      <- h2o.anomaly(iris_anomaly, iris_original, per_feature = F)
  
  # Normalize Original and Recreated H2OFrame
  iris_original.R <- data.frame(Species.setosa = ifelse(iris$Species == "setosa", 1, 0),
                                Species.versicolor = ifelse(iris$Species == "versicolor", 1, 0),
                                Species.virginica = ifelse(iris$Species == "virginica", 1, 0),
                                Species.missing = rep(0, times = 150))
  iris_original.R <- cbind(iris_original.R, iris[,1:4])
  iris_recreate.R <- as.data.frame(iris_recreate)
  
  s <- apply(iris_original.R, 2, max) - apply(iris_original.R, 2, min)
  m <- apply(iris_original.R, 2, mean)
  s[1:4] <- 1
  m[1:4] <- 0
  
  original_scaled <- t(apply(iris_original.R, 1, function(x) (x-m)/s))
  recreate_scaled <- t(apply(iris_recreate.R, 1, function(x) (x-m)/s))
  
  # Create reconstructed squared error for each feature
  reconstructed_err1 <- (original_scaled - recreate_scaled)^2
  # h2o.anomaly computes the per-row per-feature reconstruction error for the training data set
  iris_mse_per_feat <- h2o.anomaly(iris_anomaly, iris_original, per_feature = T)
  reconstructed_err2 <- as.matrix(iris_mse_per_feat) 
  
  # Check to make sure the per feature squared error is correctly calculated in h2o.anomaly
  checkEqualsNumeric(reconstructed_err1, reconstructed_err2)
  # Check to make sure the per feature squared error computes the correct MSE
  mse1 <- apply(reconstructed_err2, 1, sum)/8
  mse2 <- as.matrix(iris_mse)[,1]
  checkEqualsNumeric(mse1, mse2)
  
  
}

h2oTest.doTest("Deep Learning Anomaly Detection Per Feature Error", check.deeplearning_anomaly_mse)

