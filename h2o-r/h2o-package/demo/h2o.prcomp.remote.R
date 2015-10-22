# This is a demo of H2O's PCA function
# It imports a data set, parses it, and prints a summary
# Then, it runs PCA on a subset of the features
library(h2o)
myIP = readline("Enter IP address of H2O server: ")
myPort = readline("Enter port number of H2O server: ")
remoteH2O = h2o.init(ip = myIP, port = as.numeric(myPort), startH2O = FALSE)

australia.hex = h2o.uploadFile(remoteH2O, system.file("extdata", "australia.csv", package="h2o"), "australia.hex")
summary(australia.hex)

australia.pca = h2o.prcomp(australia.hex, k = 8)
print("Eigenvectors of the PCA model...")
print(australia.pca@model$eigenvectors)
screeplot(australia.pca)

print("Eigenvectors of the PCA model...")
australia.pca2 = h2o.prcomp(australia.hex, k = 4, transform = "STANDARDIZE")
print(australia.pca2@model$eigenvectors)
screeplot(australia.pca2)
