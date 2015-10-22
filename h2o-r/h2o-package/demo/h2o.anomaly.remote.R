# This is a demo of H2O's Anomaly function
# It imports a data set, parses it, and prints a summary
# Then, it runs Deep Learning building an Autoencoder model
# Finally it calculates the reconstruction error
# Note: This demo needs you to specify the ip address
# and port of running H2O instance.
library(h2o)
myIP = readline("Enter IP address of H2O server: ")
myPort = readline("Enter port number of H2O server: ")
remoteH2O = h2o.init(ip = myIP, port = as.numeric(myPort), startH2O = FALSE)

prostate.hex = h2o.uploadFile(remoteH2O, path = system.file("extdata", "prostate.csv", package="h2o"), destination_frame = "prostate.hex")
summary(prostate.hex)
# Set the CAPSULE, DPROS and GLEASON columns to be factor columns.
prostate.hex$CAPSULE = as.factor(prostate.hex$CAPSULE)
prostate.hex$DPROS = as.factor(prostate.hex$DPROS)
prostate.hex$GLEASON = as.factor(prostate.hex$GLEASON)

# Build autoencoder model
x = c("AGE", "RACE", "DPROS","PSA", "VOL", "CAPSULE", "GLEASON")
dl_autoencoder = h2o.deeplearning(x = x, training_frame = prostate.hex, model_id = "autoencoders", autoencoder = T)

# Detect outliers on the prostate dataset again
anomalies   = h2o.anomaly(object = dl_autoencoder, prostate.hex)
anomalies.R = as.data.frame(anomalies)

# Plot the reconstruction error and add a line for error in the 90th percentile
quantile  = h2o.quantile(anomalies$Reconstruction.MSE)
threshold = quantile["90%"]
plot(anomalies.R$Reconstruction.MSE)
abline(h=threshold)
